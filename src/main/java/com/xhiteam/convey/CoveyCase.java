package com.xhiteam.convey;

import java.util.ArrayList;
import java.util.List;

public class CoveyCase {
    String Id;

    String CaseName;

    String FirstLine;


    boolean IsLeaf;

    Integer Level;

    Integer StartLine;

    Integer EndLine;

    List<CoveyCase> ChildrenConvey;

    List<String> BodyLine;

    List<String> PreLine;


    // 临时变量判断是否需要替换
    boolean NeedReplace;

    public CoveyCase() {
        ChildrenConvey = new ArrayList<>();
        BodyLine = new ArrayList<>();
        PreLine = new ArrayList<>();
    }
}
