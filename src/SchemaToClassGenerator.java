import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

public class SchemaToClassGenerator extends DefaultHandler {
    private Deque<String> classStack = new ArrayDeque<>();
    private Map<String, List<Field>> classes = new LinkedHashMap<>();
    private String pendingElementName = null;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: SchemaToClassGenerator <schema.xsd>");
            System.exit(1);
        }
        try {
            File schemaFile = new File(args[0]);
            if (!schemaFile.exists()) {
                System.err.println("Error: Schema file not found: " + args[0]);
                System.exit(1);
            }

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();

            SchemaToClassGenerator handler = new SchemaToClassGenerator();
            parser.parse(schemaFile, handler);
            handler.writeClasses();

            System.out.println("---> Schema-to-Class generation completed successfully!");

        } catch (Exception e) {
            System.err.println("Error processing schema: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        switch (qName) {
            case "xsd:element":
            case "xs:element":
                String name = atts.getValue("name");
                if (name == null) break;
                String type = atts.getValue("type");
                String maxOccurs = atts.getValue("maxOccurs");
                boolean isList = "unbounded".equals(maxOccurs);

                if (type != null) {
                    // Named type
                    String fieldType = extractSimpleType(type);
                    if (!classStack.isEmpty()) {
                        classes.get(classStack.peek()).add(new Field(name, fieldType, isList));
                    }
                } else {
                    // Anonymous type — remember element name
                    pendingElementName = name;
                    if (!classStack.isEmpty()) {
                        String fieldType = capitalize(name);
                        classes.get(classStack.peek()).add(new Field(name, fieldType, isList));
                    }
                }
                break;

            case "xsd:complexType":
            case "xs:complexType":
                String typeName = atts.getValue("name");

                String className;
                if (typeName != null) {
                    className = capitalize(typeName);
                } else if (pendingElementName != null) {
                    className = capitalize(pendingElementName);
                    pendingElementName = null;
                } else {
                    className = "Anonymous" + classes.size();
                }

                classes.putIfAbsent(className, new ArrayList<>());
                classStack.push(className);
                break;

            case "xsd:attribute":
            case "xs:attribute":
                if (!classStack.isEmpty()) {
                    String attrName = atts.getValue("name");
                    String attrType = atts.getValue("type");
                    if (attrName != null && attrType != null) {
                        classes.get(classStack.peek()).add(new Field(attrName, extractSimpleType(attrType), false));
                    }
                }
                break;

        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("xs:complexType") || qName.equals("xsd:complexType")) {
            classStack.pop();
        }
    }

    private void writeClasses() throws IOException {
        for (Map.Entry<String, List<Field>> entry : classes.entrySet()) {
            String className = entry.getKey();
            List<Field> fields = entry.getValue();
            File outputDir = new File("output");
            if (!outputDir.exists()) {
                if(!outputDir.mkdir()){
                    throw new IOException("Failed to create output directory");
                }
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
        String lowerType = xsdType.toLowerCase();
        switch (lowerType) {
            case "string": baseType = "String"; break;
            case "int":
            case "integer": baseType = "int"; break;
            case "float": baseType = "float"; break;
            case "double": baseType = "double"; break;
            case "boolean": baseType = "boolean"; break;
            default:
                baseType = xsdType;
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

    private String extractSimpleType(String xsdType) {
        if (!xsdType.contains(":")) {
            return xsdType;
        }
        return xsdType.substring(xsdType.indexOf(':') + 1);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}