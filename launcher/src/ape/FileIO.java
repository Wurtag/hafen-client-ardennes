package ape;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

public class FileIO {
    public static String[] loadIniFile(String f) throws Exception {
        ArrayList<String> str = new ArrayList<String>();
        String ln;

        File file = new File(f);
        if (!file.exists()) file.createNewFile();

        BufferedReader br = new BufferedReader(new FileReader(f));
        while ((ln = br.readLine()) != null)
            str.add(ln);
        br.close();
        return str.toArray(new String[0]);
    }
}
