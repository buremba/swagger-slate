package org.rakam.client;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Option;
import io.github.robwin.markup.builder.markdown.MarkdownBuilder;
import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.config.CodegenConfigurator;
import io.swagger.models.Swagger;
import io.swagger.parser.Swagger20Parser;
import org.apache.commons.lang3.tuple.Pair;
import org.rakam.client.builder.document.SlateDocumentGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class Swagger2MarkupConverter {
    private final Swagger swagger;

    Swagger2MarkupConverter(Swagger swagger) {
        this.swagger = swagger;
    }

    public static Swagger2MarkupConverter from(String address) throws IOException {
        return new Swagger2MarkupConverter(new Swagger20Parser().read(address, null));
    }

    public static void main(String[] args) {
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("swagger")
                .withDescription("Swagger code generator CLI. More info on swagger.io")
                .withCommands(
                        Generate.class
                );

        builder.build().parse(args).run();
    }

    @Command(name = "generate", description = "Generate code with chosen lang")
    public static class Generate implements Runnable {
        @Option(name = {"-D"}, title = "system properties", description = "sets specified system properties in " +
                "the format of name=value,name=value")
        private String systemProperties;

        @Option(name = {"-c", "--config"}, title = "configuration file", description = "Path to json configuration file. " +
                "File content should be in a json format {\"optionKey\":\"optionValue\", \"optionKey1\":\"optionValue1\"...} " +
                "Supported options can be different for each language. Run config-help -l {lang} command for language specific config options.")
        private String configFile;

        @Option(name = {"--api-package"}, title = "api package", description = CodegenConstants.API_PACKAGE_DESC)
        private String apiPackage;

        @Option(name = {"--model-package"}, title = "model package", description = CodegenConstants.MODEL_PACKAGE_DESC)
        private String modelPackage;

        @Option(name = {"-l", "--languages"}, title = "language", required = true,
                description = "client languages separetd by comma to generate (maybe class name in classpath, required)")
        private String langs;

        @Option(name = {"-i", "--input-spec"}, title = "spec file", required = true,
                description = "location of the swagger spec, as URL or file (required)")
        private String spec;

        @Option(name = {"-o", "--output"}, title = "output directory",
                description = "where to write the generated files (current dir by default)")
        private String output = "";

        @Override
        public void run() {

            ImmutableList.Builder<CodegenConfigurator> builder = ImmutableList.builder();
            for (String lang : Splitter.on(",").trimResults().split(langs)) {
                CodegenConfigurator configurator = CodegenConfigurator.fromFile(configFile);

                if (configurator == null) {
                    configurator = new CodegenConfigurator();
                }

                if (isNotEmpty(spec)) {
                    configurator.setInputSpec(spec);
                }

                if (isNotEmpty(lang)) {
                    configurator.setLang(lang);
                }

                if (isNotEmpty(output)) {
                    configurator.setOutputDir(output);
                }

                if (isNotEmpty(apiPackage)) {
                    configurator.setApiPackage(apiPackage);
                }

                if (isNotEmpty(modelPackage)) {
                    configurator.setModelPackage(modelPackage);
                }
                setSystemProperties(configurator);
                builder.add(configurator);
            }

            try {
                MarkdownBuilder build = new SlateDocumentGenerator(builder.build()).build();
                File dir = new File(output);
                if (!dir.exists()) {
                    dir.mkdirs();
                } else
                if(dir.isFile()) {
                    throw new IllegalArgumentException("Output must be a directory");
                }
                File file = new File(dir, "slate.md");
                file.createNewFile();
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(build.toString().getBytes(Charset.forName("UTF-8")));
                fileOutputStream.close();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

        private void setSystemProperties(CodegenConfigurator configurator) {
            final Map<String, String> map = createMapFromKeyValuePairs(systemProperties);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                configurator.addSystemProperty(entry.getKey(), entry.getValue());
            }
        }

        private Map createMapFromKeyValuePairs(String commaSeparatedKVPairs) {
            final List<Pair<String, String>> pairs = parseCommaSeparatedTuples(commaSeparatedKVPairs);

            Map result = new HashMap();

            for (Pair<String, String> pair : pairs) {
                result.put(pair.getLeft(), pair.getRight());
            }

            return result;
        }

        public static List<Pair<String, String>> parseCommaSeparatedTuples(String input) {

            List<Pair<String, String>> results = new ArrayList<Pair<String, String>>();

            final List<String> tuples = splitCommaSeparatedList(input);

            for (String tuple : tuples) {
                int ix = tuple.indexOf('=');
                if (ix > 0 && ix < tuple.length() - 1) {
                    final Pair<String, String> pair = Pair.of(tuple.substring(0, ix), tuple.substring(ix + 1));
                    results.add(pair);
                }
            }

            return results;
        }

        public static List<String> splitCommaSeparatedList(String input) {

            List<String> results = new ArrayList<String>();

            if(input != null && !input.isEmpty()) {
                for (String value : input.split(",")) {
                    if(isNotEmpty(value))
                        results.add(value);
                }
            }

            return results;
        }
    }
}
