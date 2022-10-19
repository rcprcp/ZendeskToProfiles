package com.dremio.zendesktoprofile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipFileList {
    File fi;
    List<String> fileNames = new ArrayList<>();

    ZipFileList(File fi) {

        this.fi = fi;
    }

    List<String> getList() {
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(fi));
            ZipEntry zipEntry = zis.getNextEntry();
            while(zipEntry != null) {
                fileNames.add(zipEntry.getName());
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (IOException ex) {
            System.out.println("ZipFileList exception: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(46);
        }
        return fileNames;
    }
}
