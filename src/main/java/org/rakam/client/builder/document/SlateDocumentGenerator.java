package org.rakam.client.builder.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.CaseFormat;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.github.robwin.markup.builder.markdown.MarkdownBuilder;
import io.swagger.codegen.ClientOptInput;
import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.DefaultGenerator;
import io.swagger.codegen.config.CodegenConfigurator;
import io.swagger.models.ArrayModel;
import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.DateProperty;
import io.swagger.models.properties.DateTimeProperty;
import io.swagger.models.properties.DoubleProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import org.apache.commons.lang3.StringUtils;
import org.rakam.client.utils.ParameterUtils;
import org.rakam.client.utils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.rakam.client.utils.PropertyUtils.getType;

public class SlateDocumentGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SlateDocumentGenerator.class);
    private static final List supportedLanguages = ImmutableList.builder()
            .add("java")
            .add("python")
            .add("php")
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TERMS_OF_SERVICE = "Terms of service: ";
    private static final String URI_SCHEME = "URI scheme";
    private static final String HOST = "Host: ";
    private static final String BASE_PATH = "BasePath: ";
    private static final String SCHEMES = "Schemes: ";
    private final MarkdownBuilder markdownBuilder;
    private final List<CodegenConfigurator> configurators;
    private Swagger swagger;
    private Set<String> definitions;

    private Map<OperationIdentifier, Map<String, String>> templates;

    public SlateDocumentGenerator(ImmutableList<CodegenConfigurator> configurators)
    {
        this.configurators = configurators;
        markdownBuilder = new MarkdownBuilder();
        definitions = new HashSet<>();
    }

    public MarkdownBuilder build()
            throws IOException
    {
        markdownBuilder.textLine("---");
        markdownBuilder.textLine("title: API Reference");
        markdownBuilder.textLine("language_tabs:");
        markdownBuilder.textLine("  - shell");
        configurators.stream().map(c -> c.getLang()).forEach(lang -> markdownBuilder.textLine("  - " + lang));

        markdownBuilder.textLine("toc_footers:");
        markdownBuilder.textLine(" - <a href='#'>Sign Up for a Developer Key</a>");

        markdownBuilder.textLine("includes:").textLine("    - errors");
        markdownBuilder.textLine("search: true");
        markdownBuilder.textLine("---");

        this.templates = generateExampleUsages();
        buildSlateDocument();
        new DefinitionsDocument(this.swagger, markdownBuilder).process(definitions);
        return markdownBuilder;
    }

    private void buildSlateDocument()
    {
        Info info = swagger.getInfo();

        markdownBuilder.documentTitle("Introduction");

        markdownBuilder.listing("We have language bindings in " +
                configurators.stream().map(c -> c.getLang()).collect(Collectors.joining(", "))
                + "! You can view code examples in the dark area to the right, and you can switch the programming language of the examples with the tabs in the top right.");

        if (info.getDescription() != null) {
            markdownBuilder.textLine(info.getDescription());
            markdownBuilder.newLine();
        }

        if (StringUtils.isNotBlank(info.getVersion())) {
            markdownBuilder.sectionTitleLevel2("Version");
            markdownBuilder.textLine("Version: " + info.getVersion());
            markdownBuilder.newLine();
        }

        Contact contact = info.getContact();
        if (contact != null) {
            markdownBuilder.sectionTitleLevel1("Contact Information");
            if (StringUtils.isNotBlank(contact.getName())) {
                markdownBuilder.textLine("Contact: " + contact.getName());
            }
            if (StringUtils.isNotBlank(contact.getEmail())) {
                markdownBuilder.textLine("Email: " + contact.getEmail());
            }
            markdownBuilder.newLine();
        }

        License license = info.getLicense();
        if (license != null && (StringUtils.isNotBlank(license.getName()) || StringUtils.isNotBlank(license.getUrl()))) {
            markdownBuilder.sectionTitleLevel2("License");
            if (StringUtils.isNotBlank(license.getName())) {
                markdownBuilder.textLine("License: " + license.getName()).newLine();
            }
            if (StringUtils.isNotBlank(license.getUrl())) {
                markdownBuilder.textLine("License url: " + license.getUrl());
            }
            markdownBuilder.newLine();
        }

        if (StringUtils.isNotBlank(info.getTermsOfService())) {
            markdownBuilder.textLine(TERMS_OF_SERVICE + info.getTermsOfService());
            markdownBuilder.newLine();
        }

        if (StringUtils.isNotBlank(swagger.getHost()) || StringUtils.isNotBlank(swagger.getBasePath())) {
            markdownBuilder.sectionTitleLevel2(URI_SCHEME);
            if (StringUtils.isNotBlank(swagger.getHost())) {
                markdownBuilder.textLine(HOST + swagger.getHost());
            }
            if (StringUtils.isNotBlank(swagger.getBasePath())) {
                markdownBuilder.textLine(BASE_PATH + swagger.getBasePath());
            }
            if (swagger.getSchemes() != null && !swagger.getSchemes().isEmpty()) {
                List<String> schemes = swagger.getSchemes().stream().map(Scheme::toString).collect(Collectors.toList());
                markdownBuilder.textLine(SCHEMES + StringUtils.join(schemes, ", "));
            }
            markdownBuilder.newLine();
        }

        if (!swagger.getTags().isEmpty()) {
            for (Tag tag : swagger.getTags()) {
                String name = tag.getName();
                String description = tag.getDescription();
                markdownBuilder.documentTitle(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name.replaceAll("-", " "))).newLine().textLine(description).newLine();
                processOperation(name);
            }
            markdownBuilder.newLine();
        }
    }

    private void processOperation(String path, String method, Operation operation)
    {
        try {
            markdownBuilder.sectionTitleLevel1(operation.getSummary());

            StringBuilder builder = new StringBuilder();
            builder.append("curl ").append('"').append(swagger.getHost() == null ? "" : swagger.getHost()).append(path).append('"');
            if (operation.getSecurity() != null) {
                for (Map<String, List<String>> map : operation.getSecurity()) {
                    for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                        builder.append(" -H \"" + entry.getKey() + ": my" + entry.getKey() + '"');
                    }
                }
            }

            builder.append(" -X "+method);
            if(operation.getParameters().stream().anyMatch(p -> p instanceof FormParameter || p instanceof BodyParameter)) {
                builder.append(" -d @- << EOF \n" + toExampleJsonParameters(operation) + "\nEOF");
            }

            markdownBuilder.source(builder.toString(), "shell");

            for (Map.Entry<String, String> entry : templates.get(new OperationIdentifier(path, method)).entrySet()) {
                markdownBuilder.source(entry.getValue(), entry.getKey());
            }

            // TODO: response object also have example property
            Response response = operation.getResponses().get("200");
            if (response != null) {
                markdownBuilder.textLine("> The above command returns JSON structured like this:").newLine();

                Object example = response.getSchema().getExample();
                if (example != null) {
                    markdownBuilder.source(example.toString(), "json");
                }
                else {
                    String value = getValue(response.getSchema());
                    String prettyJson = mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(mapper.readValue(value, Object.class));

                    getValue(response.getSchema());

                    markdownBuilder.source(prettyJson, "json");
                }
            }

            markdownBuilder.sectionTitleLevel2("HTTP Request").textLine("`" + method + " " + path + "`");

            List<String> parameters = new ArrayList();

            if (!operation.getParameters().isEmpty()) {
                // we do not support multiple parameter types within a operation.
                Parameter parameter = operation.getParameters().get(0);
                ParameterIn parameterIn;
                try {
                    parameterIn = ParameterIn.valueOf(parameter.getIn().toUpperCase(Locale.ENGLISH));
                }
                catch (IllegalArgumentException e) {
                    throw new UnsupportedOperationException(format("Parameter type '%s' not supported yet.",
                            parameter.getIn()));
                }
                parameters.add("Parameter|Required|Type|Description");

                if (parameterIn == ParameterIn.BODY) {
                    markdownBuilder.sectionTitleLevel2("Body Parameters");

                    Model schema = ((BodyParameter) parameter).getSchema();
                    if(schema instanceof RefModel) {
                        schema = swagger.getDefinitions().get(((RefModel) schema).getSimpleRef());
                    }

                    Map<String, Property> properties;
                    if (schema instanceof ArrayModel) {
                        Property items = ((ArrayModel) schema).getItems();
                        properties = ImmutableMap.of("array", items);
                    }
                    else {
                        properties = schema.getProperties();
                    }
                    parameters.addAll(properties.entrySet().stream()
                            .map(entry -> entry.getKey() + "|" + entry.getValue().getRequired() + "|" + getType(entry.getValue(), definitions) + "|" + trimNullableText(entry.getValue().getDescription()))
                            .collect(Collectors.toList()));

                    markdownBuilder.tableWithHeaderRow(parameters);
                }
                else {
                    Map<ParameterIn, List<Parameter>> collect = operation.getParameters().stream().filter(p -> p instanceof AbstractSerializableParameter)
                            .collect(Collectors.groupingBy(a -> ParameterIn.valueOf(a.getIn().toUpperCase(Locale.ENGLISH))));

                    for (Map.Entry<ParameterIn, List<Parameter>> entry : collect.entrySet()) {
                        markdownBuilder.sectionTitleLevel2(entry.getKey().getQuery() + " Parameters");

                        entry.getValue().stream().map(p -> p.getName() + "|" + p.getRequired() +
                                "|" + ParameterUtils.getType(p, definitions) + "|" + trimNullableText(p.getDescription())).forEach(parameters::add);

                        markdownBuilder.tableWithHeaderRow(parameters);
                    }
                }
            }

            markdownBuilder.sectionTitleLevel2("Responses for status codes");
            List<String> responses = new ArrayList<>();

            String headerRow = operation.getResponses()
                    .keySet().stream()
                    .collect(Collectors.joining("|"));
            responses.add(headerRow);

            String responseRow = operation.getResponses().values().stream()
                    .map(e -> PropertyUtils.getType(e.getSchema(), definitions))
                    .collect(Collectors.joining("|"));
            responses.add(responseRow);

            markdownBuilder.tableWithHeaderRow(responses);

            String description = trimNullableText(operation.getDescription());
            if (!description.isEmpty()) {
                markdownBuilder.paragraph(description);
            }
        }
        catch (Exception e) {
            LOGGER.error(format("An error occurred while processing operation. %s %s. Skipping..",
                    method.toUpperCase(Locale.ENGLISH), path), e);
        }
    }

    private String toExampleJsonParameters(Map<String, Property> properties)
    {
        return "{" + properties.entrySet()
                .stream().map(e -> "\"" + e.getKey() + "\" : " + getValue(e.getValue()) + "\n")
                .collect(Collectors.joining(", ")) + "}";
    }

    private String toExampleJsonParameters(Operation operation)
    {
        if (operation.getParameters().size() == 1 && operation.getParameters().get(0).getIn().equals("body")) {
            Model model = ((BodyParameter) operation.getParameters().get(0)).getSchema();

            Map<String, Property> properties;
            if (model.getReference() != null) {
                String prefix = "#/definitions/";
                if (model.getReference().startsWith(prefix)) {
                    Model model1 = swagger.getDefinitions().get(model.getReference().substring(prefix.length()));
                    if (model1 instanceof ArrayModel) {
                        return prettyJson("[" + getValue(((ArrayModel) model1).getItems()) + "]");
                    }
                    properties = model1.getProperties();
                }
                else {
                    throw new IllegalStateException();
                }
            }
            else {
                properties = model.getProperties();
            }
            return prettyJson(toExampleJsonParameters(properties));
        }

        String jsonString = "{" + operation.getParameters().stream()
                .filter(e -> e instanceof FormParameter).map(e -> "\"" + e.getName() + "\" : " + getValue((AbstractSerializableParameter) e))
                .collect(Collectors.joining(", ")) + "}";

        return prettyJson(jsonString);
    }

    private static String prettyJson(String json)
    {
        try {
            return mapper.enable(SerializationFeature.INDENT_OUTPUT)
                    .writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readValue(json, Object.class));
        }
        catch (IOException e) {
            throw new IllegalStateException("Example generator couldn't generate a valid JSON");
        }
    }

    private String getValue(Property value)
    {
        if (value.getExample() != null && value.getExample() != null) {
            return value.getExample().toString();
        }
        if (value instanceof StringProperty) {
            List<String> anEnum = ((StringProperty) value).getEnum();
            if (anEnum != null && !anEnum.isEmpty()) {
                try {
                    return mapper.writeValueAsString(anEnum.get(0));
                }
                catch (JsonProcessingException e) {
                    throw Throwables.propagate(e);
                }
            }
            return "\"str\"";
        }
        else if (value instanceof IntegerProperty || value instanceof LongProperty) {
            return "1";
        }
        else if (value instanceof DoubleProperty) {
            return "1.0";
        }
        else if (value instanceof DateProperty) {
            return "\"2015-01-20\"";
        }
        else if (value instanceof BooleanProperty) {
            return "true";
        }
        else if (value instanceof MapProperty) {
            return "{\"prop\": {}}";
        }
        else if (value instanceof RefProperty) {
            Model model = swagger.getDefinitions().get(((RefProperty) value).getSimpleRef());
            return "{" + model.getProperties()
                    .entrySet()
                    .stream().map(e -> "\"" + e.getKey() + "\" : " + getValue(e.getValue()) + "")
                    .collect(Collectors.joining(", ")) + "}";
        }
        else if (value instanceof ArrayProperty) {
            return "[\n\t" + getValue(((ArrayProperty) value).getItems()) + "\n]";
        }
        else if (value instanceof ObjectProperty) {
            return "\"object\"";
        }
        else if (value instanceof DateTimeProperty) {
            return "\"2016-03-03T10:15:30.00Z\"";
        }
        else {
            throw new IllegalStateException();
        }
    }

    private String getValue(AbstractSerializableParameter value)
    {
        switch (value.getType()) {
            case "date":
                return "\"2015-01-20\"";
            case "string":
                return "\"str\"";
            case "integer":
            case "long":
                return "0";
            case "double":
                return "0.0";
            case "boolean":
                return "false";
            case "map":
                return "{\"prop\": value}";
            case "array":
                return "[\n\t" + getValue(value.getItems()) + "\n]";
            default:
                return "";
        }
    }

    private String trimNullableText(String text)
    {
        if (text == null || text.equals("null")) {
            return "";
        }
        return text.trim();
    }

    private enum ParameterIn
    {
        BODY("Body"), HEADER("Header"), FORMDATA("Form"), QUERY("Query");

        private final String query;

        ParameterIn(String query)
        {
            this.query = query;
        }

        public String getQuery()
        {
            return query;
        }
    }

    private Map<OperationIdentifier, Map<String, String>> generateExampleUsages()
            throws IOException
    {
        Map<OperationIdentifier, Map<String, String>> templates = Maps.newHashMap();

        Map<String, Map.Entry<CodegenConfig, DefaultGenerator>> languages = new HashMap<>();

        for (CodegenConfigurator configurator : configurators) {
            ClientOptInput clientOptInput = configurator.toClientOptInput();
            clientOptInput.getConfig().processOpts();

            DefaultGenerator defaultGenerator = new DefaultGenerator();
            defaultGenerator.opts(clientOptInput);

            if (!supportedLanguages.contains(configurator.getLang())) {
                throw new IllegalArgumentException(format("Language %s is not supported at the moment.", configurator.getLang()));
            }

            languages.put(configurator.getLang(), new AbstractMap.SimpleImmutableEntry<>(clientOptInput.getConfig(), defaultGenerator));

            if (swagger == null) {
                swagger = clientOptInput.getSwagger();
            }
        }

        for (Map.Entry<String, Map.Entry<CodegenConfig, DefaultGenerator>> entry : languages.entrySet()) {
            String language = entry.getKey();
            Map.Entry<CodegenConfig, DefaultGenerator> value = entry.getValue();

            Map<String, List<CodegenOperation>> operations = value.getValue().processPaths(swagger.getPaths());
            for (String parentTag : operations.keySet()) {
                List<CodegenOperation> ops = operations.get(parentTag);
                for (CodegenOperation op : ops) {
                    Map<String, Object> operation = value.getValue().processOperations(value.getKey(), parentTag, ImmutableList.of(op));

                    operation.put("modelPackage", value.getKey().modelPackage());
                    operation.put("classname", value.getKey().toApiName(parentTag));
                    operation.put("hostname", swagger.getHost());

                    for (String templateName : value.getKey().apiTemplateFiles().keySet()) {
                        String filename = value.getKey().apiFilename(templateName, parentTag);
                        if (!value.getKey().shouldOverwrite(filename) && new File(filename).exists()) {
                            continue;
                        }

                        String template;
                        URL resource = this.getClass().getClassLoader().getResource("templates/" + language + "_api_example.mustache");

                        template = Resources.toString(resource, StandardCharsets.UTF_8);

                        Template tmpl = Mustache.compiler()
                                .withLoader(name -> value.getValue().getTemplateReader(value.getKey().templateDir() + File.separator + name + ".mustache"))
                                .defaultValue("")
                                .compile(template);

                        templates.computeIfAbsent(new OperationIdentifier(op.path, op.httpMethod), key -> Maps.newHashMap()).put(language, tmpl.execute(operation));
                    }
                }
            }
        }

        return templates;
    }

    public static class OperationIdentifier
    {
        public final String path;
        public final String httpMethod;

        public OperationIdentifier(String path, String httpMethod)
        {
            this.path = path;
            this.httpMethod = httpMethod;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (!(o instanceof OperationIdentifier)) {
                return false;
            }

            OperationIdentifier that = (OperationIdentifier) o;

            if (!path.equals(that.path)) {
                return false;
            }
            return httpMethod.equals(that.httpMethod);
        }

        @Override
        public int hashCode()
        {
            int result = path.hashCode();
            result = 31 * result + httpMethod.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return "OperationIdentifier{" +
                    "path='" + path + '\'' +
                    ", httpMethod='" + httpMethod + '\'' +
                    '}';
        }
    }

    private void processOperation(String tag)
    {

        for (Map.Entry<String, Path> entry : swagger.getPaths().entrySet()) {

            Path value = entry.getValue();
            if (value.getGet() != null && value.getGet().getTags().contains(tag)) {
                processOperation(entry.getKey(), "GET", value.getGet());
            }
            if (value.getPut() != null && value.getPut().getTags().contains(tag)) {
                processOperation(entry.getKey(), "PUT", value.getPut());
            }
            if (value.getPost() != null && value.getPost().getTags().contains(tag)) {
                processOperation(entry.getKey(), "POST", value.getPost());
            }
            if (value.getDelete() != null && value.getDelete().getTags().contains(tag)) {
                processOperation(entry.getKey(), "DELETE", value.getDelete());
            }
            if (value.getPatch() != null && value.getPatch().getTags().contains(tag)) {
                processOperation(entry.getKey(), "PATH", value.getPatch());
            }
            if (value.getOptions() != null && value.getOptions().getTags().contains(tag)) {
                processOperation(entry.getKey(), "OPTIONS", value.getOptions());
            }
        }
    }
}
