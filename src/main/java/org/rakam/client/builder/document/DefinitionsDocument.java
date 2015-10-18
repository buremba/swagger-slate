package org.rakam.client.builder.document;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.robwin.markup.builder.MarkupDocBuilder;
import io.github.robwin.markup.builder.markdown.MarkdownBuilder;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;
import io.swagger.models.properties.Property;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.rakam.client.utils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Robert Winkler
 */
public class DefinitionsDocument  {
    private final Swagger swagger;
    private final MarkdownBuilder markdownBuilder;
    protected Logger logger = LoggerFactory.getLogger(getClass());

    private static final String DEFINITIONS = "Definitions";
    private static final List<String> IGNORED_DEFINITIONS = Collections.singletonList("Void");
    private static final String DESCRIPTION_FILE_NAME = "description";
    private boolean handWrittenDescriptionsEnabled;
    private String descriptionsFolderPath;


    public DefinitionsDocument(Swagger swagger, MarkdownBuilder markdownBuilder){
        this.swagger = swagger;
        this.markdownBuilder = markdownBuilder;

        if(StringUtils.isNotBlank(descriptionsFolderPath)){
            this.handWrittenDescriptionsEnabled = true;
            this.descriptionsFolderPath = descriptionsFolderPath + "/" + DEFINITIONS.toLowerCase();
        }
        if(handWrittenDescriptionsEnabled){
            if (logger.isDebugEnabled()) {
                logger.debug("Include hand-written descriptions is enabled.");
            }
        }else{
            if (logger.isDebugEnabled()) {
                logger.debug("Include hand-written descriptions is disabled.");
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Create separated definition files is disabled.");
        }
    }

    public void process(Set<String> definitions) throws IOException {
        definitions(swagger.getDefinitions().entrySet().stream()
                .filter(e -> definitions.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), markdownBuilder);
    }

    private void definitions(Map<String, Model> definitions, MarkupDocBuilder docBuilder) throws IOException {
            docBuilder.sectionTitleLevel1(DEFINITIONS);
            for(Map.Entry<String, Model> definitionsEntry : definitions.entrySet()){
                String definitionName = definitionsEntry.getKey();
                if(StringUtils.isNotBlank(definitionName)) {
                    if (checkThatDefinitionIsNotInIgnoreList(definitionName)) {
                        definition(definitions, definitionName, definitionsEntry.getValue(), docBuilder);
                        if (logger.isInfoEnabled()) {
                            logger.info("Definition processed: {}", definitionName);
                        }
                    }else{
                        if (logger.isDebugEnabled()) {
                            logger.debug("Definition was ignored: {}", definitionName);
                        }
                    }
                }

        }
    }

    private boolean checkThatDefinitionIsNotInIgnoreList(String definitionName) {
        return !IGNORED_DEFINITIONS.contains(definitionName);
    }

    private void definition(Map<String, Model> definitions, String definitionName, Model model, MarkupDocBuilder docBuilder) throws IOException {
        docBuilder.sectionTitleLevel2(definitionName);
        descriptionSection(definitionName, model, docBuilder);
        propertiesSection(definitions, definitionName, model, docBuilder);
    }

    private void propertiesSection(Map<String, Model> definitions, String definitionName, Model model, MarkupDocBuilder docBuilder) throws IOException {
        Map<String, Property> properties = getAllProperties(definitions, model);
        List<String> headerAndContent = new ArrayList();
        List<String> header = Arrays.asList("name", "description", "required", "schema", "default");
        headerAndContent.add(StringUtils.join(header,  "|"));
        if(!properties.isEmpty()){
            for (Map.Entry<String, Property> propertyEntry : properties.entrySet()) {
                Property property = propertyEntry.getValue();
                String propertyName = propertyEntry.getKey();
                List<String> content = Arrays.asList(
                        propertyName,
                        propertyDescription(definitionName, propertyName, property),
                        Boolean.toString(property.getRequired()),
                        PropertyUtils.getType(property),
                        PropertyUtils.getDefaultValue(property));
                headerAndContent.add(StringUtils.join(content, "|"));
            }
            docBuilder.tableWithHeaderRow(headerAndContent);
        }
    }

    private static Map<String, Property> getAllProperties(Map<String, Model> definitions, Model model) {
        if(model instanceof RefModel) {
            final String ref = model.getReference();
            return definitions.containsKey(ref)
                    ? getAllProperties(definitions, definitions.get(model.getReference()))
                    : null;
        }
        if(model instanceof ComposedModel) {
            ComposedModel composedModel = (ComposedModel)model;
            ImmutableMap.Builder<String, Property> allProperties = ImmutableMap.builder();
            if(composedModel.getAllOf() != null) {
                for(Model innerModel : composedModel.getAllOf()) {
                    Map<String, Property> innerProperties = getAllProperties(definitions, innerModel);
                    if(innerProperties != null) {
                        allProperties.putAll(innerProperties);
                    }
                }
            }
            return allProperties.build();
        }
        else {
            return model.getProperties();
        }
    }

    private void descriptionSection(String definitionName, Model model, MarkupDocBuilder docBuilder) throws IOException {
        if(handWrittenDescriptionsEnabled){
            String description = handWrittenPathDescription(definitionName.toLowerCase(), DESCRIPTION_FILE_NAME);
            if(StringUtils.isNotBlank(description)){
                docBuilder.paragraph(description);
            }else{
                if (logger.isInfoEnabled()) {
                    logger.info("Hand-written description cannot be read. Trying to use description from Swagger source.");
                }
                modelDescription(model, docBuilder);
            }
        }
        else{
            modelDescription(model, docBuilder);
        }
    }

    private void modelDescription(Model model, MarkupDocBuilder docBuilder) {
        String description = model.getDescription();
        if (StringUtils.isNotBlank(description)) {
            docBuilder.paragraph(description);
        }
    }

    private String propertyDescription(String definitionName, String propertyName, Property property) throws IOException {
        String description;
        if(handWrittenDescriptionsEnabled){
            description = handWrittenPathDescription(definitionName.toLowerCase() + "/" + propertyName.toLowerCase(), DESCRIPTION_FILE_NAME);
            if(StringUtils.isBlank(description)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Hand-written description file cannot be read. Trying to use description from Swagger source.");
                }
                description = StringUtils.defaultString(property.getDescription());
            }
        }
        else{
            description = StringUtils.defaultString(property.getDescription());
        }
        return description;
    }

    private String handWrittenPathDescription(String descriptionFolder, String descriptionFileName) throws IOException {
        ImmutableList<String> list = ImmutableList.of("md");
        for (String fileNameExtension : list) {
            java.nio.file.Path path = Paths.get(descriptionsFolderPath, descriptionFolder, descriptionFileName + fileNameExtension);
            if (Files.isReadable(path)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Description file processed: {}", path);
                }
                return FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8).trim();
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Description file is not readable: {}", path);
                }
            }
        }
        if (logger.isWarnEnabled()) {
            logger.info("No description file found with correct file name extension in folder: {}", Paths.get(descriptionsFolderPath, descriptionFolder));
        }
        return null;
    }
}
