package converter;

import java.io.*;
import java.util.regex.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {

        String text = ReadText.readAll("test.txt");
        text = text.replaceAll("[\r\n]", "").trim();

        if (text.charAt(0) == '<') {
            List<Element> elms = new ArrayList<>();
            analyzeXml(text, "", elms);
            elementToJson(elms);
        } else {
            Json json = new Json(text); 
            List<Entry> nodes = analyzeJson(json);
            nodeToXml(json, nodes);
        }
    }

    static Pos getXmlContents(String text, String tag) {
        String startTag = "<" + tag + ".*?>";
        String endTag = "</" + tag + ">";
        String regex = startTag;
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return null;
        }
        int i = m.end();
        int start = i;
        int end = i;
        int count = 1;
        regex = startTag + "|" + endTag;
        p = Pattern.compile(regex);
        m = p.matcher(text);
        while (count != 0) {
            if (!m.find(i)) {
                return null;
            }
            i = m.start();
            end = i;
            String str = text.substring(i, m.end());
            if (!str.endsWith("/>")) {
                if (text.startsWith("</", i)) {
                    count--;
                } else {
                    count++;
                }
            }
            i = m.end();
        }

        return new Pos(start, end);
    }

    static int search(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        int result = -1;
        if (m.find()){
            result = m.start();
        }
        return result;    
    }

    static String extract(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        String result = "";
        if (m.find()){
            result = m.group(1).trim();
        }
        return result;
    }

    static int elementToXml(Entry parent, int i, List<Element> elms, StringBuilder sb) {
        for (; i < elms.size() && parent == elms.get(i).parent;) {
            Element elm = elms.get(i);
            String endTag = "</" + elm.key + ">";
            String startTag = "<" + elm.key;
            for (Atr atr: elm.atrList) {
                startTag += " " + atr.name + "=" + atr.value; 
            }
            if (i + 1 < elms.size() && elms.get(i + 1).parent == elm.entry) {
                startTag += ">";
                sb.append(startTag);
                i = elementToXml(elm.entry, i + 1, elms, sb);
                sb.append(endTag);
            } else {
                String value = elm.value;
                if ("null".equals(value)) {
                    startTag += " />";
                    sb.append(startTag);
                } else {
                    startTag += ">";
                    sb.append(startTag);
                    sb.append(value);    
                    sb.append(endTag);
                }
                i++;
            }
        }
        return i;
    }
   
    static void nodeToXml(Json json, List<Entry> nodes) {
        List<Element> elms = new ArrayList<>();
        Entry root = nodes.get(0);
        int count = json.getChildrenCount(root);
        for (Entry entry: nodes) {
            elms.add(json.toElement(entry));
        }

        int startIndex = 1;
        Entry rootEntry = root;
        if (count > 1) {
            startIndex = 0;
            rootEntry = null;
            elms.get(0).key = "root";
        }
        StringBuilder sb = new StringBuilder();
        elementToXml(rootEntry, startIndex, elms, sb);
        String output = sb.toString();
        output = output.replaceAll("<#.+?>|<\\/#.+?>", "");
//        output = output.replaceAll("<@>.*?</@>|<>.*?<\\/>", "");

        System.out.println(output);
    }

    static boolean isProcessJsonChild(Json json, Entry entry) {
        if ("\"\"".equals(entry.key) || "\"@\"".equals(entry.key) || "\"#\"".equals(entry.key)) {
            return false;
        }
        if ("\"#element\"".equals(entry.key) && json.hasChild(entry)) {
            return true;
        }
        if (entry.key.startsWith("\"#") && json.hasArray(entry)) {
            return true;
        }
//        if (entry.key.startsWith("\"@") && json.hasChild(entry)) {
//            return true;
//        }
        if (entry.key.startsWith("\"@") || entry.key.startsWith("\"#")) {
            return false;
        }
        return true;
    } 

    static void analyzeJsonChild(Json json, Entry entry, List<Entry> nodes) {
        while (entry != null) {
            if (isProcessJsonChild(json, entry)) {
                nodes.add(entry);

                if (json.hasArray(entry)) {
                    List<JsonArray> jsonArray = json.getArray(entry);
                    for (JsonArray item: jsonArray) {
                        if ("[]".equals(item.value) || "{}".equals(item.value)) {
                            nodes.add(new Entry("", "", 0, 0, entry, true));
                            continue;
                        }
                        Entry arrayChild = new Entry("", item.value, item. from, item.to, entry, true);
                        if (json.hasArray(arrayChild)) {
                            analyzeJsonChild(json, arrayChild, nodes);
                        } else  if (json.hasChild(arrayChild)) {
                            analyzeJsonChild(json, arrayChild, nodes);
                        } else {
                            nodes.add(arrayChild);
                        }
                    }
                }
            
                if (json.hasChild(entry)) {
                    analyzeJsonChild(json, json.getChild(entry), nodes);
                }
            }
            entry = json.getSibling(entry);
        }
    }

    static List<Entry> analyzeJson(Json json) {
        Entry root = json.getRootEntry();
        Entry child = json.getChild(root);
        List<Entry> nodes = new ArrayList<>();
        analyzeJsonChild(json, child, nodes);
        List<Entry> delNodes = new ArrayList<>();
        List<Entry> nodes2 = new ArrayList<>();
        nodes2.add(root);

        for (Entry entry: nodes) {
            if (json.findList(entry, delNodes)) {
                delNodes.remove(entry);
            } else {
                nodes2.add(entry);
            }
            List<Entry> children = json.getChildren(entry);
            if (json.hasAtr(children)) {
                analyzeJsonPreAtr(json, children, nodes2, delNodes);
                analyzeJsonAtr(json, entry, children, nodes2);      
            }                      
        }
/*
        for (Entry entry: nodes2) {
            json.printEntry(entry);
        }
*/ 
        return nodes2;
    }

    static void analyzeJsonPreAtr(Json json, List<Entry> children, List<Entry> nodes2, List<Entry> delNodes) {
        List<Entry> preAtrList = json.getPreAtr(children);
        for (Entry entry: preAtrList) {
            nodes2.add(entry);
            delNodes.add(entry);
        }
    }

    static void analyzeJsonAtr(Json json, Entry entry, List<Entry> children, List<Entry> nodes2) {
        boolean validAtrFlag = true;
        boolean hashMatchFlag = false;
        boolean noAtrFlag = true;
        boolean noHashFlag = false;
        boolean otherAtrFlag = false;
        boolean atrHasChildFlag = false;
        boolean valueHasChildFlag = false;

        Entry hashEntry = json.getHash(children);
        if (hashEntry == null) {
            noHashFlag = true;
        } else {
            if (entry.arrayFlag && "".equals(entry.key)) {
                if ("\"#element\"".equals(hashEntry.key)) {
                    hashMatchFlag = true;
                }
            } else {
                valueHasChildFlag = json.hasChild(hashEntry);
                if (hashEntry.key.substring(2).equals(entry.key.substring(1))) {
                    hashMatchFlag = true;
                }
            }
        }

        for (Entry child: children) {
            if (child.key.startsWith("\"@"))  {
                noAtrFlag = false;
                if ("\"@\"".equals(child.key)) {
                    validAtrFlag = false;
                }
                continue;
            }
            if (child.key.startsWith("\"#"))  {
                continue;
            }
            otherAtrFlag = true;
        }

        if (otherAtrFlag) {
            for (Entry child: children) {
                if ("\"\"".equals(child.key) || "\"@\"".equals(child.key) || "\"#\"".equals(child.key)) {
                    continue;
                }
                String key = "";
                if (child.key.startsWith("\"@") || child.key.startsWith("\"#")) {
                    key = "\"" + child.key.substring(2);
                } else {
                    key = child.key;
                }
                Entry findEntry = json.findChild(key, entry);
                if (findEntry == null) {
                    String value = child.value;
                    addNodes(key, value, child, entry, nodes2);
                } else {
                    findEntry.from = child.from;
                    findEntry.to = child.to;
                }
            }
            return;
        }

        if (noAtrFlag && !hashMatchFlag) {
            String key = "\"" + hashEntry.key.substring(2);
            String value = hashEntry.value;
            addNodes(key, value, hashEntry, entry, nodes2);
        }

        for (Entry child: children) {
            if (child.key.startsWith("\"@"))  {
                if (!"\"@\"".equals(child.key) && !json.hasChild(child)) {
                    if (hashMatchFlag) {
                        String childValue = "null".equals(child.value) ? "\"\"" : child.value;  
                        if ("[\"\"]".equals(childValue)) {
                            validAtrFlag = false;
                            String key = "\"" + child.key.substring(2);
                            Entry newEntry = addNodes(key, "", child, entry, nodes2);   
                            addArrayNodes("", "", child, newEntry, nodes2);   
                        } else {
                            entry.atrList.add(new Atr(child.key, childValue));
                        }
                    } else {
                        String key = "\"" + child.key.substring(2);
                        String value = child.value;
                        addNodes(key, value, child, entry, nodes2);

                        if (!noHashFlag) {
                            key = "\"" + hashEntry.key.substring(2);
                            value = hashEntry.value;
                            addNodes(key, value, child, entry, nodes2);
                        }
                    }
                } else {
                    if (!hashMatchFlag && "\"#\"".equals(hashEntry.key)) {
                        entry.value = "\"\"";
                    }
                }    
            }
        }

        for (Entry child: children) {
            if (child.key.startsWith("\"@") && !"\"@\"".equals(child.key) && json.hasChild(child)) {
                atrHasChildFlag = true;
                String key = "\"" + child.key.substring(2);
                String value = "";
                Entry newEntry = addNodes(key, value, child, entry, nodes2);
                List<Entry> atrChildren = json.getChildren(child);
                for (Entry atrChild: atrChildren) {
                    if (!"\"@\"".equals(atrChild.key) && !"\"#\"".equals(atrChild.key) && !"\"\"".equals(atrChild.key)) {
                        addNodes(atrChild.key, atrChild.value, atrChild, newEntry, nodes2);
                    } else {
                        validAtrFlag = false;
                    }
                }       
            }
        }

        if (hashMatchFlag) {
            if (validAtrFlag) {
               entry.value = hashEntry.value;
            } else {
               String key = "\"" + hashEntry.key.substring(2);
               String value = hashEntry.value;
               addNodes(key, value, hashEntry, entry, nodes2);
             }    
         }
 
        if (valueHasChildFlag) {
            if (atrHasChildFlag) {
                String key = "\"" + hashEntry.key.substring(2);
                String value = hashEntry.value;
                Entry newEntry = addNodes(key, value, hashEntry, entry, nodes2);
                List<Entry> hashChildren = json.getChildren(hashEntry);
                for (Entry hashChild: hashChildren) {
                    addNodes(hashChild.key, hashChild.value, hashChild, newEntry, nodes2);
                }
            } else {
                if (hashMatchFlag) {
                    List<Entry> hashChildren = json.getChildren(hashEntry);
                    for (Entry hashChild: hashChildren) {
                        Entry newEntry = addNodes(hashChild.key, hashChild.value, hashChild, entry, nodes2);
                        if (json.hasChild(hashChild)) {
                            analyzeJsonAtr(json, newEntry, json.getChildren(hashChild), nodes2);
                        }
                    }   
                }
            }
        }
    }

    static Entry addNodes(String key, String value, Entry  ref, Entry parent, List<Entry> nodes2) {
        Entry newEntry = new Entry(key, value, ref.from, ref.to, parent);
        nodes2.add(newEntry);
        return newEntry;
    }

    static Entry addArrayNodes(String key, String value, Entry  ref, Entry parent, List<Entry> nodes2) {
        Entry newEntry = new Entry(key, value, ref.from, ref.to, parent, true);
        nodes2.add(newEntry);
        return newEntry;
    }

    static int elementToJsonObj(String parentStr, int i, List<Element> elms, boolean arrayFlag, StringBuilder sb) {
        boolean firstFlag = true;
        if (arrayFlag) {
            sb.append("[");
        } else {
            sb.append("{");
        }
        for (; i < elms.size() && parentStr.equals(elms.get(i).parentStr);) {
            Element elm = elms.get(i);
            if (firstFlag) {
                firstFlag = false;
            } else {
                sb.append(",");
            }
            if (!elm.arrayItemFlag) {
                sb.append("\"" + elm.key + "\":");
            }
            boolean atrFlag = false;
            if (!elm.atrList.isEmpty()) {
                atrFlag = true;
                sb.append("{");
                for (int j = 0; j < elm.atrList.size(); j++) {
                    Atr atr = elm.atrList.get(j);
                    String atrValue = changeQuote(atr.value);
                    sb.append("\"@" + atr.name + "\":" + atrValue + ",");
                }
                sb.append("\"#" + elm.key + "\":");              
            }
            if (i + 1 < elms.size() && elms.get(i + 1).parentStr.equals(elm.path)) {
                i = elementToJsonObj(elm.path, i + 1, elms, elm.arrayFlag, sb);
            } else {
                String elmValue = changeQuote(elm.value); 
                sb.append(elmValue);
                i++;
            }
            if (atrFlag) {
                sb.append("}");
            }
        }
        if (arrayFlag) {
            sb.append("]");
        } else {
            sb.append("}");
        }
        return i;
    }

    static String changeQuote(String str) {
        if (str.equals("''")) {
            return "\"\"";
        }
        String newStr = "";
        if (!str.startsWith("'")) { 
            return str;
        }
        newStr = str.substring(1);
        if (newStr.endsWith("'")) {
            newStr = newStr.substring(0, newStr.length() - 1);
        }
        return "\"" + newStr + "\"";
    }

    static void elementToJson(List<Element> elms) {
        StringBuilder sb = new StringBuilder();
        elementToJsonObj("", 0, elms, false, sb); 
        System.out.println(sb.toString());
    }

    static void analyzeXml(String text, String path, List<Element> elms) {
        String str = text;
        str = str.replaceAll("<\\?.+?>", "");
        analyzeXmlChild(str, path, elms);
        Element.markArray(elms);
    }

    static void analyzeXmlChild(String text, String path, List<Element> elms) {
        String str = text;
        String regex = "<(.+?)>";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(str);
        String startTag = "";
        if (m.find()){
            startTag = m.group(1);
        }
        if ("".equals(startTag)) {
            return;
        }

        String tag = startTag;
        String[] strs = tag.split(" ");
        if (strs.length > 1) {
            tag = strs[0];
            startTag = "<" + startTag + ">";
        } else {
            startTag = "<" + tag + ">";
        }
        String endTag = "</" + tag + ">";

        Pos pos;
        String contents = "";
        if (startTag.endsWith("/>")) {
            contents = "null";
            pos = new Pos(0, startTag.length());
        } else {
            pos = getXmlContents(str, tag);
            if (pos != null) {
                contents = str.substring(pos.start, pos.end).trim();
                pos.end += endTag.length();
            }    
        }

        String newPath = path;
        if (!"".equals(newPath)) {
            newPath += ",";
        }
        newPath += tag;
        List<Atr> atrList = analyzeXmlAtr(startTag);       // atr
        String value = analyzeXmlValue(contents);   // value

        elms.add(new Element(value, atrList, newPath));

        analyzeXmlChild(contents, newPath, elms);   // child

        if (pos != null) {
            str = str.substring(pos.end).trim();
        } else {
            str = "";
        }
        if (!"".equals(str)) {
            analyzeXmlChild(str, path, elms);  // sibling
        }
    }
    
    static String analyzeXmlValue(String text) {
        String str = text;
        String regex = "<(.+?)>";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(str);
        String startTag = "";
        if (m.find()){
            startTag = m.group(1);
        }
        if ("".equals(startTag)) {
            if ("null".equals(text)) {
                return "null";
            } else {
                return "\"" + text + "\"";
            }
        }
        return "";
    }

    static List<Atr> analyzeXmlAtr(String element) {
        List<Atr> atrList = new ArrayList<>();
        if (element.indexOf("=") == -1)  {
            return atrList;
        } 
        String str = element;
        str = str.replaceAll("[<>/]", "").trim();
        int index = str.indexOf(" ");
        if (index == -1) {
            return atrList;
        }
        str = str.substring(index).trim();
        while (str.length() > 0) {
            int i = str.indexOf("=");
            String name = str.substring(0, i).trim();
            str = str.substring(i).trim();
            str = str.substring(1).trim(); //=
            i = str.indexOf(" ");
            String value = "";
            if (i == -1) {
                value = str;
                str = "";
            } else {
                value = str.substring(0, i);
                str = str.substring(i).trim();
            }
            atrList.add(new Atr(name, value));
        }
        return atrList;
    } 
}

class Json {

    String text;
    String path = "";

    Json(String text) {
        this.text = cutsp(text);
    }

    Entry getRootEntry() {
        return new Entry("", text, 0, text.length() - 1, null);
    }

    Entry getChild(Entry entry) {
        int i = entry.from + entry.key.length();
        if (entry.key.length() > 0) {
            i++;
        }
        if (text.charAt(i) != '{') {
            return null;
        }
        if (text.charAt(i + 1) == '}') {
            return new Entry("", "", i, i + 1, entry);
        }
        int from = i + 1;
        i = text.indexOf(":", from);
        if (i > entry.to || i == -1) {
            return null;
        }
        String key = text.substring(from, i);
        i++;
        int j = getEndIndex(i);
        String value = text.substring(i, j + 1);
        Entry child = new Entry(key, value, from, j, entry);
        return child;
    }

    Entry getSibling(Entry entry) { 
        if (entry.arrayFlag) {
            return null;
        }
        int from = entry.to + 1;
        if (text.charAt(from) != ',') {
            return null;
        }
        from++;
        int i = text.indexOf(":", from);
        String key = text.substring(from, i);
        i++;
        int j = getEndIndex(i);
        String value = text.substring(i, j + 1);
        Entry sibling = new Entry(key, value, from, j, entry.parent);
        return sibling;
    }

    List<Entry> getChildren(Entry entry) {
        List<Entry> list = new ArrayList<>();
        Entry child = getChild(entry);
        while (child != null) {
            list.add(child);
            child = getSibling(child);
        }
        return list;
    }

    int getChildrenCount(Entry entry) {
        List<Entry> children = getChildren(entry);
        return children.size();
    }

    Entry findChild(String key, Entry entry) {
        List<Entry> children = getChildren(entry);
        for (Entry child: children) {
            if (key.equals(child.key)) {
                return child;
            }
        }
        return null;
    }

    boolean hasArray(Entry entry) {
        if (entry.value.startsWith("[")) {
            return true;
        }
        return false;
    }

    List<JsonArray> getArray(Entry entry) {
        List<JsonArray> jsonArray = new ArrayList<>();
        int i = entry.from + entry.key.length() + 1;
        if (entry.key.length() > 0) {
            i++;
        }
        for (; i < entry.to;) {
            int j = getEndIndex(i);
            jsonArray.add(new JsonArray(text.substring(i, j + 1), i, j));
            i = j + 1;
            if (i < text.length() && text.charAt(i) == ',') {
                i++;
            }
        }
        return jsonArray;
    }

    String cutsp(String str) {
        StringBuilder sb = new StringBuilder();
        int quatFlag = 0;
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch == '"') {
                quatFlag ^= 1;
            }
            if (ch != ' ' || quatFlag == 1) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
    
    int search(int from, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text.subSequence(from, text.length()));
        int result = -1;
        if (m.find()){
            result = m.start();
        }
        return result + from;    
    }
   
    int getEndIndex(int from) {
        char ch = text.charAt(from);
        if (ch != '{' && ch !='[') {
            return search(from + 1, ",|\\{|\\}|\\[|\\]") - 1;
        }

        char sch = '{';
        char ech = '}';
        if (ch == '[') {
            sch = '[';
            ech = ']';
        } 
        int i = from + 1;
        int count = 1;
        for (; i < text.length() && count != 0; i++) {
            if (text.charAt(i) == sch) {
                count++;
            }
            if (text.charAt(i) == ech) {
                count--;
            }
        }
        return i - 1;
    }

    boolean hasChild(Entry entry) {
        int i = entry.from + entry.key.length();
        if (entry.key.length() > 0) {
            i++;
        }
        if (text.charAt(i) != '{') {
            return false;
        }
        if (text.charAt(i + 1) == '}') {
            return false;
        }
        int from = i + 1;
        i = text.indexOf(":", from);
        if (i > entry.to || i == -1) {
            return false;
        }
        return true;
    }

    boolean hasSibling(Entry entry) {
//        if (entry.arrayFlag) {
//            return false;
//        }
        int from = entry.to + 1;
        if (text.charAt(from) != ',') {
            return false;
        }
        return true;
    }

    boolean hasAtr(List<Entry> children) {
        for (Entry child: children) {
            String key = child.key;
            if ("\"@\"".equals(key) || "\"#\"".equals(key)) {
                continue;
            }
            if (key.startsWith("\"@") || key.startsWith("\"#")) {
                return true;
            }
        }
        return false;
    } 

    List<Entry> getPreAtr(List<Entry> children) {
        List<Entry> list = new ArrayList<>();
        for (Entry child: children) {
            String key = child.key;
            if (key.startsWith("\"@") || key.startsWith("\"#")) {
                return list;
            }
            list.add(child);
        }
        return list;
    }
    
    Entry getHash(List<Entry> children) {
        for (Entry child: children) {
            String key = child.key;
            if (key.startsWith("\"#")) {
                return child;
            }
        }
        return null;
    }
    
    boolean findList(Entry entry, List<Entry> list) {
        for (Entry item: list) {
            if (item.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    Element toElement(Entry entry) {

        String key = cutQuote(entry.key);
        if (entry.arrayFlag && "".equals(key)) {
            key ="element";
        }
        String value = "";
        String path = getPath(entry);
        Entry parent = entry.parent;
        List<Atr> atrList = new ArrayList<>();

        if (!"".equals(entry.value)) {
            value = entry.value;
            if ("{}".equals(value) || "[]".equals(value)) {
                value = "\"\"";
            } 
            if (value.startsWith("{")) {
                value = "";
            }
        }
        value = cutQuote(value);
        if (!entry.atrList.isEmpty()) {
            for (Atr atr: entry.atrList) {
                String atrName = cutQuote(atr.name);
                if (atrName.startsWith("@")) {
                    atrName = atrName.substring(1);
                }   
                String atrValue = atr.value;            
                if ("true".equals(atrValue)) {
                    atrValue = "\"true\"";
                }
                if ("false".equals(atrValue)) {
                    atrValue = "\"false\"";
                }
                if ("{}".equals(atrValue)) {
                    atrValue = "\"\"";
                }
                if ("[]".equals(atrValue)) {
                    atrValue = "\"\"";
                }
                atrList.add(new Atr(atrName, atrValue));           
            }
        }
        return new Element(key, value, atrList, path, entry, parent);
    }

    void printEntry(Entry entry) {

        System.out.println("Element:");
        System.out.println("path = " + getPath(entry));
        if (!"".equals(entry.value)) {
            String value = entry.value;
            if ("{}".equals(value)) {
                value = "\"\"";
            } 
            if (!value.startsWith("{")) {
                System.out.println("value = " + value);
            }
        }
        if (!entry.atrList.isEmpty()) {
            System.out.println("attributes:");
            for (Atr atr: entry.atrList) {
                String atrName = cutQuote(atr.name);
                if (atrName.startsWith("@")) {
                    atrName = atrName.substring(1);
                }
                System.out.println(atrName + " = " + atr.value);           
            }
        }
        System.out.println();
    }

    String getPath(Entry entry) {
        String path = cutQuote(entry.key);
        Entry parent = entry.parent;
        while (parent != null) {
            path = cutQuote(parent.key) + ", " + path;
            parent = parent.parent;
        }
        if (path.startsWith("," )) {
            path = path.substring(2);
        }
        return path;
    }

    String cutQuote(String str) {
        if (str.startsWith("\"")) {
            str = str.substring(1);
        }
        if (str.endsWith("\"")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;        
    }
}

class Atr {
    String name;
    String value;

    Atr(String name, String value) {
        this.name = name;
        this.value = value;
    }
}

class Element {
    String key;
    String value;
    String path;
    Entry entry;
    Entry parent;
    List<Atr> atrList;
    String parentStr;
    boolean arrayFlag = false;
    boolean arrayItemFlag = false;

    Element(String key, String value, List<Atr> atrList, String path, Entry entry, Entry parent) {
        this.key = key;
        this.value = value;
        this.atrList = atrList;
        this.path = path;
        this.entry = entry;
        this.parent = parent;
    }

    Element(String value, List<Atr> atrList, String path) {
        this.value = value;
        this.atrList = atrList;
        this.path = path;
        int i = path.lastIndexOf(',');
        if (i == -1) {
            parentStr = "";
            key = path;
        } else {
            parentStr = path.substring(0, i);
            key = path.substring(i + 1);
        }
    }

    static ArrayElement findArrayElement(ArrayElement aelm, Map<ArrayElement, Integer> list) {
        for (ArrayElement item: list.keySet()) {
            if (aelm.key.equals(item.key) && aelm.path.equals(item.path)) {
                return item;
            }
        }
        return null;
    }

    static ArrayElement findArrayElement(ArrayElement aelm, List<ArrayElement> list) {
        for (ArrayElement item: list) {
            if (aelm.key.equals(item.key) && aelm.path.equals(item.path)) {
                return item;
            }
        }
        return null;
    }

    static void markArray(List<Element> elms) { 
        Map<ArrayElement, Integer> arrayMap = new HashMap<>();
        for (Element elm: elms) {
            if (!"".equals(elm.value) && !"null".equals(elm.value) && !elm.value.startsWith("\"")) {
                continue;
            }
            ArrayElement aelm = new ArrayElement(elm.key, elm.parentStr);
            ArrayElement findAelm = findArrayElement(aelm, arrayMap); 
            if (findAelm == null) {
                arrayMap.put(aelm, 1);
            } else {
                arrayMap.put(findAelm, arrayMap.get(findAelm) + 1);
            }
        }

        List<ArrayElement> arrayList = new ArrayList<>();
        for (ArrayElement aelm: arrayMap.keySet()) {
            if (arrayMap.get(aelm) > 1) {
                arrayList.add(aelm);
            }
        }

        for (ArrayElement aelm: arrayList) {

            ArrayElement parentAelm = getParentArrayElemet(aelm);
            if (findArrayElement(parentAelm, arrayList) != null) {
                continue;
            }

            for (Element elm: elms) {
                if (elm.parentStr.equals(aelm.path) && elm.key.equals(aelm.key)) {
                    elm.arrayItemFlag = true;
                    continue;
                }
                if (elm.parentStr.equals(parentAelm.path) && elm.key.equals(parentAelm.key)) {
                    elm.arrayFlag = true;
                    continue;
                }
            }
        }
    }

    static ArrayElement getParentArrayElemet(ArrayElement aelm) {
        String path = aelm.path;

        int i = path.lastIndexOf(",");
        String parentPath = i == -1 ? "" : path.substring(0, i);
        int j = i + 1;
        String parentKey = path.substring(j);
        return new ArrayElement(parentKey, parentPath);
    }

}

class ArrayElement {
    String path;
    String key;

    ArrayElement(String key, String path) {
        this.key = key;
        this.path = path;
    }
}

class Entry {
    String key;
    String value;
    int from;
    int to;
    Entry parent;
    boolean arrayFlag;
    List<Atr> atrList;

    Entry(String key, String value, int from, int to, Entry parent, boolean arrayFlag) {
        this.key = key;
        this.value = value;
        this.from = from;
        this.to = to;
        this.parent = parent;
        this.arrayFlag = arrayFlag;
        atrList = new ArrayList<>();

        if (this.value.matches("[0-9.]+")) {
            this.value = "\"" + this.value + "\"";
        }
    }
    
    Entry(String key, String value, int from, int to, Entry parent) {
        this(key, value, from, to, parent, false);
    }

    boolean equals(Entry other) {
        return key.equals(other.key) &&
               value.equals(other.value) &&
               from == other.from &&
               to == other.to &&
               parent == other.parent &&
               arrayFlag == other.arrayFlag;
    }
}

class JsonArray {

    String value;
    int from;
    int to;

    JsonArray(String value, int from, int to) {
        this.value = value;
        this.from = from;
        this.to = to;
    }
}

class Pos {
    int start;
    int end;

    Pos(int start, int end) {
        this.start = start;
        this.end = end;
    }
}

class ReadText {

    static String readAll(String fileName) {
        StringBuilder str = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            String rec = "";
            while ((rec = br.readLine()) != null) {
                str.append(rec);
            }
            br.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return str.toString();
    }
}
