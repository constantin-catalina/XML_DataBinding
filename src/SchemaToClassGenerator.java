import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

public class SchemaToClassGenerator extends DefaultHandler {
    private String currentClass = null;
    private Map<String, List<Field>> classes = new LinkedHashMap<>();

    private boolean inComplexType = false;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: SchemaToClassGenerator <schema.xsd>");
            System.exit(1);
        }
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();

            SchemaToClassGenerator handler = new SchemaToClassGenerator();
            parser.parse(new File(args[0]), handler);
            handler.writeClasses();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        switch (qName) {
            case "xs:complexType":
                inComplexType = true;
                String typeName = atts.getValue("name");
                if (typeName != null) {
                    classes.put(typeName, new ArrayList<>());
                    currentClass = typeName;
                }
                break;

            case "xs:element":
                if (inComplexType && currentClass != null) {
                    String fieldName = atts.getValue("name");
                    String type = atts.getValue("type");
                    String maxOccurs = atts.getValue("maxOccurs");
                    if (fieldName != null && type != null) {
                        boolean isList = "unbounded".equals(maxOccurs);
                        classes.get(currentClass).add(new Field(fieldName, type, isList));
                    }
                } else {
                    String elemName = atts.getValue("name");
                    String type = atts.getValue("type");
                    if (elemName != null) {
                        currentClass = capitalize(elemName);
                        classes.putIfAbsent(currentClass, new ArrayList<>());
                        if (type != null) {
                            classes.get(currentClass).add(new Field(elemName.toLowerCase(), type, false));
                        }
                    }
                }
                break;

            case "xs:attribute":
                if (inComplexType && currentClass != null) {
                    String name = atts.getValue("name");
                    String type = atts.getValue("type");
                    if (name != null && type != null) {
                        classes.get(currentClass).add(new Field(name, type, false));
                    }
                }
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("xs:complexType")) {
            inComplexType = false;
            currentClass = null;
        }
        if (qName.equals("xs:element") && currentClass != null) {
            currentClass = null;
        }
    }

    private void writeClasses() throws IOException {
        for (Map.Entry<String, List<Field>> entry : classes.entrySet()) {
            String className = entry.getKey();
            List<Field> fields = entry.getValue();
            File outputDir = new File("output");
            if (!outputDir.exists()) {
                outputDir.mkdir();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputDir, className + ".java")))) {
                boolean needsListImport = fields.stream().anyMatch(f -> f.isList);
                if (needsListImport) {
                    writer.write("import java.util.ArrayList;\n");
                    writer.write("import java.util.List;\n\n");
                }
                writer.write("public class " + className + " {\n");
                for (Field f : fields) {
                    String javaType = mapType(f.type, f.isList);
                    if (f.isList) {
                        writer.write("    public List<" + mapType(f.type, false) + "> " + f.name + " = new ArrayList<>();\n");
                    } else {
                        writer.write("    public " + javaType + " " + f.name + ";\n");
                    }
                }
                writer.write("}\n");
            }
            System.out.println("Generated " + className + ".java");
        }
    }

    private String mapType(String xsdType, boolean list) {
        String baseType;
        switch (xsdType) {
            case "xs:string": baseType = "String"; break;
            case "xs:int": case "xs:integer": baseType = "int"; break;
            case "xs:float": baseType = "float"; break;
            case "xs:double": baseType = "double"; break;
            case "xs:boolean": baseType = "boolean"; break;
            default:
                baseType = xsdType.contains(":") ? xsdType.substring(xsdType.indexOf(':') + 1) : xsdType;
        }
        return list ? "List<" + baseType + ">" : baseType;
    }

    private static class Field {
        String name;
        String type;
        boolean isList;
        Field(String name, String type, boolean isList) {
            this.name = name;
            this.type = type;
            this.isList = isList;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
