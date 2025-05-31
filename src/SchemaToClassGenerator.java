import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

public class SchemaToClassGenerator extends DefaultHandler {
    private final Deque<ClassContext> contextStack = new ArrayDeque<>();
    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, String> typeDefinitions = new HashMap<>(); // name -> type mapping

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
            factory.setNamespaceAware(true);
            SAXParser parser = factory.newSAXParser();

            SchemaToClassGenerator handler = new SchemaToClassGenerator();
            parser.parse(schemaFile, handler);
            handler.resolveTypeReferences();
            handler.writeClasses();

            System.out.println("---> Schema-to-Class generation completed successfully!");

        } catch (Exception e) {
            System.err.println("Error processing schema: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        switch (localName) {
            case "element":
                handleElement(atts);
                break;

            case "complexType":
                handleComplexType(atts);
                break;

            case "simpleType":
                handleSimpleType(atts);
                break;

            case "attribute":
                handleAttribute(atts);
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (localName) {
            case "complexType":
                if (!contextStack.isEmpty()) {
                    contextStack.pop();
                }
                break;
            case "element":
                if (!contextStack.isEmpty()) {
                    ClassContext current = contextStack.peek();
                    if (current.isAnonymous && current.elementName != null) {
                        contextStack.pop();
                    }
                }
                break;
        }
    }

    private void handleElement(Attributes atts) {
        String name = atts.getValue("name");
        String type = atts.getValue("type");
        String ref = atts.getValue("ref");
        String maxOccurs = atts.getValue("maxOccurs");
        String minOccurs = atts.getValue("minOccurs");

        boolean isList = "unbounded".equals(maxOccurs);

        if (ref != null) {
            if (!contextStack.isEmpty()) {
                String refType = extractTypeName(ref);
                assert contextStack.peek() != null;
                contextStack.peek().classInfo.fields.add(
                        new Field(refType.toLowerCase(), refType, isList)
                );
            }
            return;
        }

        if (name == null) return;

        if (type != null) {
            String fieldType = resolveTypeName(type);
            if (!contextStack.isEmpty()) {
                contextStack.peek().classInfo.fields.add(
                        new Field(name, fieldType, isList)
                );
            } else {
                String className = capitalize(name);
                ClassInfo classInfo = new ClassInfo(className, false);
                classes.put(className, classInfo);
            }
        } else {
            String className = generateClassName(name);
            ClassInfo classInfo = new ClassInfo(className, false);

            if (!contextStack.isEmpty()) {
                contextStack.peek().classInfo.fields.add(
                        new Field(name, className, isList)
                );
            }

            classes.put(className, classInfo);
            contextStack.push(new ClassContext(classInfo, true, name));
        }
    }

    private void handleComplexType(Attributes atts) {
        String typeName = atts.getValue("name");

        if (typeName != null) {
            String className = capitalize(typeName);
            ClassInfo classInfo = new ClassInfo(className, false);
            classes.put(className, classInfo);
            typeDefinitions.put(typeName, className);
            contextStack.push(new ClassContext(classInfo, false, null));
        } else {
            if (contextStack.isEmpty()) {
                String className = "AnonymousType" + classes.size();
                ClassInfo classInfo = new ClassInfo(className, true);
                classes.put(className, classInfo);
                contextStack.push(new ClassContext(classInfo, true, null));
            }
        }
    }

    private void handleSimpleType(Attributes atts) {
        String typeName = atts.getValue("name");
        if (typeName != null) {
            typeDefinitions.put(typeName, "String");
        }
    }

    private void handleAttribute(Attributes atts) {
        if (contextStack.isEmpty()) return;

        String attrName = atts.getValue("name");
        String attrType = atts.getValue("type");
        String use = atts.getValue("use");

        if (attrName != null && attrType != null) {
            String fieldType = resolveTypeName(attrType);
            assert contextStack.peek() != null;
            contextStack.peek().classInfo.fields.add(
                    new Field(attrName, fieldType, false)
            );
        }
    }

    private String generateClassName(String elementName) {
        String baseName = capitalize(elementName);
        String className = baseName;
        int counter = 1;

        while (classes.containsKey(className)) {
            className = baseName + counter++;
        }

        return className;
    }

    private String resolveTypeName(String type) {
        if (type == null) return "Object";

        String cleanType = extractTypeName(type);

        if (typeDefinitions.containsKey(cleanType)) {
            return typeDefinitions.get(cleanType);
        }

        return mapXsdType(cleanType);
    }

    private String extractTypeName(String qualifiedName) {
        if (qualifiedName == null) return null;
        int colonIndex = qualifiedName.indexOf(':');
        return colonIndex >= 0 ? qualifiedName.substring(colonIndex + 1) : qualifiedName;
    }

    private void resolveTypeReferences() {
        for (ClassInfo classInfo : classes.values()) {
            for (Field field : classInfo.fields) {
                if (typeDefinitions.containsKey(field.type)) {
                    field.type = typeDefinitions.get(field.type);
                }
            }

            if (classInfo.baseClass != null && typeDefinitions.containsKey(classInfo.baseClass)) {
                classInfo.baseClass = typeDefinitions.get(classInfo.baseClass);
            }
        }
    }

    private void writeClasses() throws IOException {
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            if (!outputDir.mkdir()) {
                throw new IOException("Failed to create output directory");
            }
        }

        for (ClassInfo classInfo : classes.values()) {
            writeClass(classInfo, outputDir);
        }
    }

    private void writeClass(ClassInfo classInfo, File outputDir) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(new File(outputDir, classInfo.name + ".java")))) {

            Set<String> imports = new HashSet<>();
            boolean needsList = classInfo.fields.stream().anyMatch(f -> f.isList);

            if (needsList) {
                imports.add("java.util.ArrayList");
                imports.add("java.util.List");
            }

            for (String imp : imports) {
                writer.write("import " + imp + ";\n");
            }
            if (!imports.isEmpty()) {
                writer.write("\n");
            }

            writer.write("public class " + classInfo.name);
            if (classInfo.baseClass != null && !classInfo.baseClass.equals("Object")) {
                writer.write(" extends " + classInfo.baseClass);
            }
            writer.write(" {\n");

            for (Field field : classInfo.fields) {
                writeField(writer, field);
            }

            writer.write("}\n");
            System.out.println("Generated " + classInfo.name + ".java");
        }
    }

    private void writeField(BufferedWriter writer, Field field) throws IOException {
        String javaType;
        String initialization = "";

        if (field.isList) {
            String elementType = field.type;
            javaType = "List<" + elementType + ">";
            initialization = " = new ArrayList<>()";
        } else {
            javaType = field.type;
        }

        writer.write("    public " + javaType + " " + field.name + initialization + ";\n");
    }

    private String mapXsdType(String xsdType) {
        if (xsdType == null) return "String";

        switch (xsdType.toLowerCase()) {
            case "string": return "String";
            case "int":
            case "integer": return "int";
            case "long": return "long";
            case "float": return "float";
            case "double": return "double";
            case "boolean": return "boolean";
            default:
                return capitalize(xsdType);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static class ClassContext {
        ClassInfo classInfo;
        boolean isAnonymous;
        String elementName;

        ClassContext(ClassInfo classInfo, boolean isAnonymous, String elementName) {
            this.classInfo = classInfo;
            this.isAnonymous = isAnonymous;
            this.elementName = elementName;
        }
    }

    private static class ClassInfo {
        String name;
        boolean isAnonymous;
        String baseClass;
        List<Field> fields;

        ClassInfo(String name, boolean isAnonymous) {
            this.name = name;
            this.isAnonymous = isAnonymous;
            this.fields = new ArrayList<>();
        }
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
}