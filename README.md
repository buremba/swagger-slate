# swagger-slate
Generates Slate documentation from your Swagger specification


# Building

###### Requirements
- Java 8

```sh
git clone https://github.com/buremba/swagger-slate.git
cd swagger-slate
mvn clean install -DskipTests
```

# Running
```sh
java -jar target/client.slate-*-jar-with-dependencies.jar generate -l java,python,php -i src/main/resources/rakam-example-spec.json -o ./
```

Currently, the supported languages are `php`, `python` `java`. `javascript` for [swagger-js](https://github.com/swagger-api/swagger-js) will be supported in first release.

# Example


# Contribution
Currently, the project is far from being mature and has many bugs & missing features.
Oauth support, `path` and `header` parameter types are not supported yet.
You are more than welcomed to contribute the project.
