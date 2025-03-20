## Springboot template with gradlew

### Pre Requisites
1. install docker & docker compose - `brew install --cask docker`
1. install `asdf` - https://asdf-vm.com/guide/getting-started.html#official-download
1. configure `asdf`
```shell
cat .tool-versions | awk '{print $1}' | xargs -I _ asdf plugin add _
asdf direnv setup --shell zsh --version latest
asdf direnv allow
asdf direnv install
asdf current java 2>&1 > /dev/null
    if [[ "$?" -eq 0 ]]
    then
        export JAVA_HOME=$(asdf where java)
        echo "Java home set successfully."
    fi
```

### Run build

```shell
./gradlew clean build
```

## Code Flow Analyzer

This project includes a lightweight code flow analyzer that can help you understand how methods call each other within your Spring Boot application. The analyzer doesn't rely on JavaParser, making it compatible with any Java version.

### Features

- **Dependency-free analysis** - Works with just the JDK
- **Spring-aware** - Understands Spring Boot components (controllers, services, repositories)
- **REST API support** - Access code analysis through HTTP endpoints
- **CLI utilities** - Run analysis directly from the command line

### Ways to Run the Code Analyzer

#### 1. Using the REST API (Spring Boot Application)

Start the Spring Boot application with the `codeanalysis` profile:

```shell
./gradlew :mod1:bootRun --args="--spring.profiles.active=codeanalysis"
```

Then access the code analysis through the REST endpoints:

* Analyze a specific method:
```
curl http://localhost:32000/mod1/api/codeanalysis/flow/rest/UserController/getUsersAboveAge | jq
```

* Simplified endpoint analysis:
```
curl http://localhost:32000/mod1/api/codeanalysis/endpoint/getUsersAboveAge | jq
```

#### 2. Using the Simple Demo Script

For a quick analysis without starting the Spring Boot application:

```shell
./run-simple-analysis.sh
```

You can also specify the class and method to analyze:

```shell
./run-simple-analysis.sh com.sbtl1.mod1.service.UserService getUsersByAge
```

#### 3. Using the Standalone Demo

Another way to run the analysis is through the standalone demo:

```shell
# Compile the necessary files
javac -d out mod1/src/main/java/com/sbtl1/mod1/util/SimpleCodeFlowAnalyzer.java mod1/src/main/java/com/sbtl1/mod1/util/SimpleCallFlowAnalysisDemo.java

# Run the analysis
java -cp out com.sbtl1.mod1.util.SimpleCallFlowAnalysisDemo com.sbtl1.mod1.rest.UserController getUsersAboveAge
```

### Understanding the Output

The analyzer produces a call graph showing the chain of method calls:

```
Call Graph for com.sbtl1.mod1.rest.UserController.getUsersAboveAge:
com.sbtl1.mod1.rest.UserController.getUsersAboveAge
  com.sbtl1.mod1.service.UserService.getUsersByAge
    com.sbtl1.mod1.dao.UserRepository.findByAgeGreaterThan
```

This shows that:
- `UserController.getUsersAboveAge()` calls
  - `UserService.getUsersByAge()` which calls
    - `UserRepository.findByAgeGreaterThan()`

### How It Works

The analyzer:
1. Locates and reads Java source files
2. Uses regular expressions to identify method declarations and calls
3. Follows the chain of calls through the codebase
4. Builds a call graph representing the flow of execution

### Limitations

- Analysis is based on static code examination, not runtime behavior
- Complex language constructs might not be correctly analyzed
- Dynamic method calls (reflection, lambdas) are not fully supported