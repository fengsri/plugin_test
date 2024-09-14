package com.xhiteam.convey;

import java.util.ArrayList;
import java.util.List;

public class CoveyCase {
    String Id;

    String CaseName;

    RowString FirstLine;


    boolean IsLeaf;

    Integer Level;

    Integer StartLine;

    Integer EndLine;

    List<CoveyCase> ChildrenConvey;

    List<RowString> BodyLine;

    List<RowString> PreLine;


    // 临时变量判断是否需要替换
    boolean NeedReplace;

    public CoveyCase() {
        ChildrenConvey = new ArrayList<>();
        BodyLine = new ArrayList<>();
        PreLine = new ArrayList<>();
    }
}
