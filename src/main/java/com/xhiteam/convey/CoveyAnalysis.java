package com.xhiteam.convey;

import com.intellij.openapi.util.text.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoveyAnalysis {

    public String createConveyTree(String data, String row) {
        // 获取Covey数据
        List<String> conveyDataLine = getConveyDataLine(data);

        // 构建Covey树结构
        CoveyCase buildCase = buildCase(conveyDataLine, 0, "", 1, new ArrayList<>());
        fullBodyLine(conveyDataLine, buildCase);

        // 根据光标的行代码去做替换
        setNeedReplace(buildCase, row, false);

        // 构建原Covey的代码结构 , 现在替换的Covey代码
        String coveyCode = buildCoveyCode(buildCase, true, "", row, false, false);
        String originCoveyCode = buildOriginCoveyCode(conveyDataLine);

        // 替换函数
        return data.replace(originCoveyCode, coveyCode);
    }

    private void fullBodyLine(List<String> lineList, CoveyCase convey) {
        if (lineList.size() <= 0) {
            return;
        }
        if (convey.StartLine == 0 && convey.EndLine == 0) {
            return;
        }
        List<String> body = new ArrayList<>();
        for (int i = convey.StartLine + 1; i < convey.EndLine; i++) {
            body.add(lineList.get(i));
        }
        convey.BodyLine = body;
        for (int i = 0; i < convey.ChildrenConvey.size(); i++) {
            fullBodyLine(lineList, convey.ChildrenConvey.get(i));
        }
    }

    private List<String> getConveyDataLine(String tmp) {
        String[] splitList = Strings.trim(tmp).split("\n");

        // 定义临时变量
        List<String> data = new ArrayList<>();
        int start = 0;
        int tag = 0;

        for (int i = 0; i < splitList.length; i++) {
            String v = splitList[i];
            String tmpLine = Strings.trim(v);
            if (tmpLine.contains("(t *testing.T)")) {
                continue;
            }
            if ((tmpLine.startsWith("Convey(") || tmpLine.startsWith("FocusConvey(")) && tmpLine.endsWith("{") && tmpLine.contains("t")) {
                start = i;
                break;
            }
        }

        for (int i = start; i < splitList.length; i++) {
            String v = splitList[i];
            String tmpLine = Strings.trim(v);
            if ((tmpLine.startsWith("Convey(") || tmpLine.startsWith("FocusConvey(")) && tmpLine.endsWith("{")) {
                if (tag == 0) {
                    tag = countTab(v);
                }
            }
            if (tag > 0) {
                data.add(v);
            }
            if (tmpLine.equals("})") && tag == countTab(v)) {
                break;
            }
        }
        return data;
    }

    private CoveyCase buildCase(List<String> lineList, int start, String parentId, int level, List<String> prefixLine) {
        if ((lineList.size() - 1) <= start) {
            return null;
        }
        int tag = countTab(lineList.get(start));
        String CaseName = "";
        Matcher matcher = Pattern.compile("\".*\"").matcher(Strings.trim(lineList.get(start)));
        if (matcher.find()) {
            CaseName = matcher.group().replaceAll("\"", "");
        }
        String Id = parentId + "_" + CaseName;
        if (parentId.length() <= 0) {
            Id = CaseName;
        }
        CoveyCase node = new CoveyCase();
        node.Id = Id;
        node.FirstLine = lineList.get(start);
        node.CaseName = CaseName;
        node.StartLine = start;
        node.Level = level;
        node.PreLine = prefixLine;

        List<CoveyCase> childrenList = new ArrayList<>();
        List<String> preLine = new ArrayList<>();
        for (int i = start + 1; i < lineList.size(); i++) {
            String item = Strings.trim(lineList.get(i));
            if ((item.startsWith("Convey(") || item.startsWith("FocusConvey(")) && item.endsWith("{")) {
                CoveyCase children = buildCase(lineList, i, node.Id, level + 1, preLine);
                if (children != null) {
                    childrenList.add(children);
                    i = children.EndLine;
                    preLine = new ArrayList<>();
                    continue;
                }
            }
            if (item.equals("})") && tag == countTab(lineList.get(i))) {
                node.EndLine = i;
                if (childrenList.size() > 0) {
                    node.ChildrenConvey = childrenList;
                } else {
                    node.IsLeaf = true;
                }
                return node;
            } else {
                preLine.add(lineList.get(i));
            }
        }
        return node;
    }

    private boolean setNeedReplace(CoveyCase coveyCase, String row, boolean setChildren) {
        if (coveyCase == null) {
            return false;
        }
        if (coveyCase.IsLeaf) {
            if (setChildren) {
                coveyCase.NeedReplace = true;
                return false;
            }
            return false;
        }
        if (!setChildren) {
            setChildren = coveyCase.FirstLine.equals(row);
        }
        if (setChildren) {
            coveyCase.NeedReplace = true;
        }
        boolean isMath = false;
        boolean backMath = false;
        for (int i = 0; i < coveyCase.ChildrenConvey.size(); i++) {
            CoveyCase childrenConvey = coveyCase.ChildrenConvey.get(i);
            if (setChildren) {
                setNeedReplace(childrenConvey, row, true);
                continue;
            }
            if (!isMath) {
                isMath = childrenConvey.FirstLine.equals(row);
                backMath = setNeedReplace(childrenConvey, row, isMath);
                if (backMath) {
                    isMath = true;
                    childrenConvey.NeedReplace = true;
                }
            }
        }
        if (isMath || backMath) {
            coveyCase.NeedReplace = true;
        }
        return isMath || backMath;
    }


    private int countTab(String s) {
        String[] parts = s.split("\t");
        return parts.length - 1;
    }

    private String buildOriginCoveyCode(List<String> origin) {
        StringBuilder data = new StringBuilder();
        for (String s : origin) {
            data.append(s).append("\n");
        }
        return data.toString();
    }

    private String buildCoveyCode(CoveyCase coveyCase, boolean isFirst, String data, String row, boolean use, boolean isFocusConvey) {
        data = headerFunc(coveyCase, isFirst, data, use, isFocusConvey);
        data = bodyFunc(coveyCase, data);
        if (!coveyCase.IsLeaf && coveyCase.FirstLine.equals(row)) {
            use = true;
            if (coveyCase.NeedReplace && Strings.trim(coveyCase.FirstLine).startsWith("Convey")) {
                isFocusConvey = true;
            }
        }
        for (int i = 0; i < coveyCase.ChildrenConvey.size(); i++) {
            CoveyCase childrenConvey = coveyCase.ChildrenConvey.get(i);
            data = buildCoveyCode(childrenConvey, false, data, row, use, isFocusConvey);
        }
        data += fullPrefix("\t", coveyCase.Level, "})\n");
        return data;
    }

    private String headerFunc(CoveyCase coveyCase, boolean isFirst, String result, boolean use, boolean isFocusConvey) {
        StringBuilder resultBuilder = new StringBuilder(result);
        for (int i = 0; i < coveyCase.PreLine.size(); i++) {
            resultBuilder.append(coveyCase.PreLine.get(i)).append("\n");
        }
        result = resultBuilder.toString();
        if (use) {
            if (isFocusConvey) {
                result += fullPrefix("\t", coveyCase.Level, "FocusConvey(\"" + coveyCase.CaseName + "\", func() {\n");
            } else {
                result += fullPrefix("\t", coveyCase.Level, "Convey(\"" + coveyCase.CaseName + "\", func() {\n");
            }
            return result;
        }
        if (isFirst) {
            if (coveyCase.NeedReplace && Strings.trim(coveyCase.FirstLine).startsWith("Convey")) {
                result += fullPrefix("\t", coveyCase.Level, "FocusConvey(\"" + coveyCase.CaseName + "\", t, func() {\n");
            } else {
                result += fullPrefix("\t", coveyCase.Level, "Convey(\"" + coveyCase.CaseName + "\", t, func() {\n");
            }
        }

        if (!isFirst) {
            if (coveyCase.NeedReplace && Strings.trim(coveyCase.FirstLine).startsWith("Convey")) {
                result += fullPrefix("\t", coveyCase.Level, "FocusConvey(\"" + coveyCase.CaseName + "\", func() {\n");
            } else {
                result += fullPrefix("\t", coveyCase.Level, "Convey(\"" + coveyCase.CaseName + "\", func() {\n");
            }
        }
        return result;
    }

    private String bodyFunc(CoveyCase coveyCase, String result) {
        if (coveyCase.IsLeaf) {
            StringBuilder resultBuilder = new StringBuilder(result);
            for (int i = 0; i < coveyCase.BodyLine.size(); i++) {
                resultBuilder.append(coveyCase.BodyLine.get(i)).append("\n");
            }
            result = resultBuilder.toString();
        }
        return result;
    }


    // FullPrefix 字符串填充
    private String fullPrefix(String full, int count, String origin) {
        String s = "";
        for (int i = 0; i < count; i++) {
            s += full;
        }
        return s + origin;
    }

}


