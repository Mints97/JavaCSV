package javacsv;

import java.io.IOException;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
	    try (CSVFile file = new CSVFile("test.csv")) {
            for (CSVFile.LineContainer line : file.getMappedFirstLineIterable()) {
                System.out.printf("FIELD1 = %s, FIELD2 = %d, FIELD3 = %s", line.getString("FIELD1"),
                        line.getInt("FIELD2"), line.getString("FIELD3"));
                System.out.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
