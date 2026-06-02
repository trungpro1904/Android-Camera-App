package com.example.myapplication;

import android.graphics.ColorMatrix;

public class LutItem {
    private final String name;
    private final String fileName;
    private final ColorMatrix colorMatrix;

    public LutItem(String name, String fileName, ColorMatrix colorMatrix) {
        this.name = name;
        this.fileName = fileName;
        this.colorMatrix = colorMatrix;
    }

    public String getName() { return name; }
    public String getFileName() { return fileName; }
    public ColorMatrix getColorMatrix() { return colorMatrix; }
}

