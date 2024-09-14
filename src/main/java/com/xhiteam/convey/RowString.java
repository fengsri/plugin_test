package com.xhiteam.convey;

public class RowString {
    String string;
    int num;

    public RowString(String data, int num) {
        this.string = data;
        this.num = num;
    }

    public boolean equals(Object obj) {
        if (obj instanceof RowString tmp) {
            return tmp.string.equals(string) && tmp.num == num;
        }
        return false;
    }
}
