package javacsv;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Opens and reads a CSV file. Conforms to the RFC 4180 specification.
 *
 * Example usage:
 * <pre>
 *     {@code
 *
 *     try(CSVFile file = new CSVFile("./file_name.csv")) {
 *         if (file.skipFirstLine()) {
 *             for (String[] line : file.getIterable()) {
 *                 // do something with the strings contained in the line
 *             }
 *         }
 *     } catch (IOException e) {
 *         e.printStackTrace();
 *     }
 *
 *     }
 * </pre>
 *
 * Created by Maxim Mints on 5/16/2017.
 */
public class CSVFile implements AutoCloseable {
    /* Based on <a href="https://stackoverflow.com/a/769713/3079266">https://stackoverflow.com/a/769713/3079266</a>.
     * The regex for splitting the line was borrowed from there.
     */
    private static final String LINE_SPLIT_REGEX = ",(?=(?:[^\"]*\"[^\"]*\")*(?![^\"]*\"))";

    public static class LineContainer extends HashMap<String, Object> {
        @SuppressWarnings("unchecked")
        private static <T> T genericValueProvider(String input, Class<T> tClass) {
            if (tClass.isAssignableFrom(String.class)) {
                return (T) input;
            } else if (tClass.isAssignableFrom(Integer.class)) {
                return (T) Integer.valueOf(input);
            } else if (tClass.isAssignableFrom(Boolean.class)) {
                return (T) Boolean.valueOf(input);
            } else if (tClass.isAssignableFrom(Double.class)) {
                return (T) Double.valueOf(input);
            } else {
                throw new IllegalArgumentException("Bad type.");
            }
        }

        public <T> T get(String key, Class<T> tClass) {
            return genericValueProvider(((String) get(key)).trim(), tClass);
        }

        public int getInt(String key) {
            return get(key, Integer.class);
        }

        public String getString(String key) {
            return ((String) get(key)).trim();
        }

        public double getDouble(String key) {
            return get(key, Double.class);
        }

        public boolean getBoolean(String key) {
            return get(key, Boolean.class);
        }
    }

    private static final Iterable<LineContainer> emptyMappedIterable = () -> new Iterator<LineContainer>() {

        /**
         * Returns {@code true} if the iteration has more elements.
         * (In other words, returns {@code true} if {@link #next} would
         * return an element rather than throwing an exception.)
         *
         * @return {@code true} if the iteration has more elements
         */
        @Override
        public boolean hasNext() {
            return false;
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        @Override
        public LineContainer next() {
            throw new NoSuchElementException("No elements in iteration!");
        }
    };

    private BufferedReader reader;

    /**
     * Creates a CSV file handler from a Reader, which is used to create a BufferedReader.
     *
     * @param reader the Reader to use.
     */
    public CSVFile(Reader reader) {
        this.reader = new BufferedReader(reader);
    }

    /**
     * Creates a CSV file handler for a remote CSV file specified by a URL and accessed via HTTP.
     *
     * @param remoteFile the URL to the remote CSV file.
     * @throws IOException if the URL could not be opened.
     */
    public CSVFile(URL remoteFile) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) remoteFile.openConnection();

        if (conn.getResponseCode() == 200) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            throw new IOException("Bad response from server! Response code " + conn.getResponseCode());
        }
    }

    /**
     * Creates a CSV file handler from a file.
     *
     * @param file                   the file to use.
     * @throws FileNotFoundException if the file does not exist.
     */
    public CSVFile(File file) throws FileNotFoundException {
        this(new FileReader(file));
    }

    /**
     * Creates a CSV file handler from a file.
     *
     * @param file                   the path to the file to use.
     * @throws FileNotFoundException if the file does not exist.
     */
    public CSVFile(String file) throws FileNotFoundException {
        this(new File(file));
    }

    /**
     * Separates a string representing a CSV line into an array of strings for each of the
     * comma-delimetered values.
     *
     * @param line the line to be split.
     * @return the resulting array of strings.
     */
    public static String[] splitLine(String line) {
        String[] values = line.split(LINE_SPLIT_REGEX);

        // escape values
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();

            if (values[i].startsWith("\"") && values[i].endsWith("\"")) {
                values[i] = values[i].substring(1, values[i].length() - 1);

                if (values[i].contains("\"\"")) {
                    values[i] = values[i].replace("\"\"", "\"");
                }
            }
        }

        return values;
    }

    /**
     * Checks if a string is a valid CSV line.
     * A valid CSV line will only have the comma separator either between the separated
     * entries, or inside an entry which is fully enclosed in double quotes. Also, an entry fully
     * enclosed in these quotes should have all double quote symbols in it escaped by doubling.
     *
     * @param line the line to be checked.
     * @return {@code true} if the line is not {@code null} and is a valid CSV line.
     */
    public static boolean isValidLine(String line) {
        if (line == null) {
            return false;
        }

        for (String str : line.split(LINE_SPLIT_REGEX)) {
            str = str.trim();

            if (str.length() > 0) {
                if (str.contains(",") && (str.charAt(0) != '"' || str.charAt(str.length() - 1) != '"')) {
                    return false;
                } else if (str.charAt(0) == '"' || str.charAt(str.length() - 1) == '"') {
                    String strQuoted = str.substring(1, str.length() - 1);

                    if (strQuoted.replace("" + '"' + '"', "").contains("" + '"')) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Reads and discards the first line of the file. Can be used to ignore the header row.
     *
     * @return {@code true} if the line has been read and has an even number of {@code "} symbols.
     */
    public boolean skipFirstLine() {
        return getIterable().iterator().hasNext();
    }

    /**
     * Gets an Iterable providing an array of strings in each line of a CSV file while they are
     * valid CSV.
     *
     * @return the Iterable that can be used in a foreach loop to enumerate the lines of the CSV file.
     */
    public Iterable<String[]> getIterable() {
        /* use the fact that Iterable<T> is technically a functional interface to save a few bytes.
         * This lambda is the iterator() method. */
        return () -> new Iterator<String[]>() {
            private String line;

            /**
             * Returns {@code true} if the iteration has more elements.
             * (In other words, returns {@code true} if {@link #next} would
             * return an element rather than throwing an exception.)
             *
             * @return {@code true} if the iteration has more elements
             */
            @Override
            public boolean hasNext() {
                try {
                    line = reader.readLine();
                    return isValidLine(line);
                } catch (IOException e) {
                    if (System.err != null) {
                        e.printStackTrace();
                    }

                    return false;
                }
            }

            /**
             * Returns the next element in the iteration.
             *
             * @return the next element in the iteration
             * @throws NoSuchElementException if the iteration has no more elements
             */
            @Override
            public String[] next() throws NoSuchElementException {
                if (line == null) {
                    throw new NoSuchElementException("No more lines to read!");
                }

                //line = line.replace("\"\"", "\\\"");

                return splitLine(line);
            }
        };
    }

    /**
     * Gets an Iterable providing a map to the strings in each line of a CSV file while they are
     * valid CSV. The first line is skipped and is used as the keys for the map.
     * Useful for Excel-style tables with headers.
     *
     * @return the Iterable that can be used in a foreach loop to enumerate the lines of the CSV file.
     */
    public Iterable<LineContainer> getMappedFirstLineIterable() {
        try {
            String firstLine = reader.readLine();
            if (isValidLine(firstLine)) {
                return getMappedIterable(splitLine(firstLine));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return emptyMappedIterable;
    }

    /**
     * Gets an Iterable providing a map to the strings in each line of a CSV file while they are
     * valid CSV. The keys for the map should be provided.
     *
     * @param fieldNames the names of the fields of the document, to be used as keys of the maps returned
     *                   by the iteration.
     * @return the Iterable that can be used in a foreach loop to enumerate the lines of the CSV file.
     */
    public Iterable<LineContainer> getMappedIterable(String[] fieldNames) {
        /*
         * use the fact that Iterable<T> is technically a functional interface to save a few bytes.
         * This lambda is the iterator() method.
         */
        return () -> new Iterator<LineContainer>() {
            private String line;

            /**
             * Returns {@code true} if the iteration has more elements.
             * (In other words, returns {@code true} if {@link #next} would
             * return an element rather than throwing an exception.)
             *
             * @return {@code true} if the iteration has more elements
             */
            @Override
            public boolean hasNext() {
                try {
                    line = reader.readLine();
                    return isValidLine(line);
                } catch (IOException e) {
                    if (System.err != null) {
                        e.printStackTrace();
                    }

                    return false;
                }
            }

            /**
             * Returns the next element in the iteration.
             *
             * @return the next element in the iteration
             * @throws NoSuchElementException if the iteration has no more elements
             */
            @Override
            public LineContainer next() {
                if (line == null) {
                    throw new NoSuchElementException("No more lines to read!");
                }

                LineContainer result = new LineContainer();
                String[] values = splitLine(line);

                for (int i = 0; i < values.length && i < fieldNames.length; i++) {
                    result.put(fieldNames[i], values[i]);
                }

                return result;
            }
        };
    }

    /**
     * Closes the file reader.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        reader.close();
    }
}
