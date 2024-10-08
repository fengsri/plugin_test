package com.xhiteam.convey;

import com.intellij.openapi.util.text.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoveyAnalysis {

    public String createConveyTree(String data, RowString row, int funcNum) {
        // 获取Covey数据
        List<RowString> conveyDataLine = getConveyDataLine(data, funcNum);

        // 构建Covey树结构
        CoveyCase buildCase = buildCase(conveyDataLine, 0, "", 1, new ArrayList<>());
        fullBodyLine(conveyDataLine, buildCase);

        // 根据光标的行代码去做替换
        boolean convey = Strings.trim(row.string).startsWith("Convey");
        setNeedReplace(buildCase, row, false, convey);

        // 构建原Covey的代码结构 , 现在替换的Covey代码
        String coveyCode = buildCoveyCode(buildCase, "");
        String originCoveyCode = buildOriginCoveyCode(conveyDataLine);

        // 替换函数
        return data.replace(originCoveyCode, coveyCode);
    }

    private void fullBodyLine(List<RowString> lineList, CoveyCase convey) {
        if (lineList.size() <= 0) {
            return;
        }
        if (convey.StartLine == 0 && convey.EndLine == 0) {
            return;
        }
        List<RowString> body = new ArrayList<>();
        for (int i = convey.StartLine + 1; i < convey.EndLine; i++) {
            body.add(lineList.get(i));
        }
        convey.BodyLine = body;
        for (int i = 0; i < convey.ChildrenConvey.size(); i++) {
            fullBodyLine(lineList, convey.ChildrenConvey.get(i));
        }
    }

    private List<RowString> getConveyDataLine(String tmp, int funcRow) {
        String[] splitList = Strings.trim(tmp).split("\n");

        // 定义临时变量
        List<RowString> data = new ArrayList<>();
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
                    tag = countTab(new RowString(v, 0));
                }
            }
            if (tag > 0) {
                data.add(new RowString(v, i + funcRow));
            }
            if (tmpLine.equals("})") && tag == countTab(new RowString(v, 0))) {
                break;
            }
        }
        return data;
    }

    private CoveyCase buildCase(List<RowString> lineList, int start, String parentId, int level, List<RowString> prefixLine) {
        if ((lineList.size() - 1) <= start) {
            return null;
        }
        int tag = countTab(lineList.get(start));
        String CaseName = "";
        Matcher matcher = Pattern.compile("\".*\"").matcher(Strings.trim(lineList.get(start).string));
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
        List<RowString> preLine = new ArrayList<>();
        for (int i = start + 1; i < lineList.size(); i++) {
            String item = Strings.trim(lineList.get(i).string);
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


    private boolean setNeedReplace(CoveyCase coveyCase, RowString row, boolean setGlobal, boolean convey) {
        if (coveyCase == null) {
            return false;
        }
        // 叶子结点
        if (coveyCase.IsLeaf) {
            if (setGlobal) {
                if (convey && Strings.trim(coveyCase.FirstLine.string).startsWith("Convey")) {
                    coveyCase.NeedReplace = true;
                }
                if (!convey && Strings.trim(coveyCase.FirstLine.string).startsWith("FocusConvey")) {
                    coveyCase.NeedReplace = true;
                }
                return false;
            }
            return false;
        }

        // 父级节点
        if (!setGlobal) {
            setGlobal = coveyCase.FirstLine.equals(row);
        }
        if (setGlobal) {
            if (convey && Strings.trim(coveyCase.FirstLine.string).startsWith("Convey")) {
                coveyCase.NeedReplace = true;
            }
            if (!convey && Strings.trim(coveyCase.FirstLine.string).startsWith("FocusConvey")) {
                coveyCase.NeedReplace = true;
            }
        }

        // 设置子节点
        boolean currentMatch = false;
        boolean backMatch = false;
        for (int i = 0; i < coveyCase.ChildrenConvey.size(); i++) {
            CoveyCase childrenConvey = coveyCase.ChildrenConvey.get(i);
            if (setGlobal) {
                setNeedReplace(childrenConvey, row, true, convey);
                continue;
            }
            if (!currentMatch) {
                currentMatch = childrenConvey.FirstLine.equals(row);
                backMatch = setNeedReplace(childrenConvey, row, currentMatch, convey);
                if (backMatch) {
                    currentMatch = true;
                }
            }
        }

        // 判断下当前层级是否还有FocusConvey
        boolean hasFocusConvey = false;
        for (int i = 0; i < coveyCase.ChildrenConvey.size(); i++) {
            CoveyCase childrenConvey = coveyCase.ChildrenConvey.get(i);
            if (childrenConvey.NeedReplace && Strings.trim(childrenConvey.FirstLine.string).startsWith("Convey")) {
                hasFocusConvey = true;
                break;
            }
            if (!childrenConvey.NeedReplace && Strings.trim(childrenConvey.FirstLine.string).startsWith("FocusConvey")) {
                hasFocusConvey = true;
                break;
            }
        }

        if (currentMatch || backMatch) {
            if (convey && Strings.trim(coveyCase.FirstLine.string).startsWith("Convey")) {
                coveyCase.NeedReplace = true;
            }
            if (!convey && Strings.trim(coveyCase.FirstLine.string).startsWith("FocusConvey")) {
                if (!hasFocusConvey) {
                    coveyCase.NeedReplace = true;
                }
            }
        }
        return currentMatch || backMatch;
    }


    private int countTab(RowString s) {
        String[] parts = s.string.split("\t");
        return parts.length - 1;
    }

    private String buildOriginCoveyCode(List<RowString> origin) {
        StringBuilder data = new StringBuilder();
        for (RowString s : origin) {
            data.append(s.string).append("\n");
        }
        return data.toString();
    }

    private String buildCoveyCode(CoveyCase coveyCase, String data) {
        data = headerFunc(coveyCase, data);
        data = bodyFunc(coveyCase, data);
        for (int i = 0; i < coveyCase.ChildrenConvey.size(); i++) {
            CoveyCase childrenConvey = coveyCase.ChildrenConvey.get(i);
            data = buildCoveyCode(childrenConvey, data);
        }
        data += fullPrefix("\t", coveyCase.Level, "})\n");
        return data;
    }

    private String headerFunc(CoveyCase coveyCase, String result) {
        StringBuilder resultBuilder = new StringBuilder(result);
        for (int i = 0; i < coveyCase.PreLine.size(); i++) {
            resultBuilder.append(coveyCase.PreLine.get(i).string).append("\n");
        }
        result = resultBuilder.toString();
        if (coveyCase.NeedReplace) {
            if (Strings.trim(coveyCase.FirstLine.string).startsWith("Convey")) {
                result += coveyCase.FirstLine.string.replace("Convey", "FocusConvey") + "\n";
            }
            if (Strings.trim(coveyCase.FirstLine.string).startsWith("FocusConvey")) {
                result += coveyCase.FirstLine.string.replace("FocusConvey", "Convey") + "\n";
            }
        } else {
            result += coveyCase.FirstLine.string + "\n";
        }
        return result;
    }

    private String bodyFunc(CoveyCase coveyCase, String result) {
        if (coveyCase.IsLeaf) {
            StringBuilder resultBuilder = new StringBuilder(result);
            for (int i = 0; i < coveyCase.BodyLine.size(); i++) {
                resultBuilder.append(coveyCase.BodyLine.get(i).string).append("\n");
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


