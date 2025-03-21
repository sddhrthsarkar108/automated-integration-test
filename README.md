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

This project includes a code flow analyzer that helps you understand how methods call each other within your Spring Boot application using **JavaParserCodeFlowAnalyzer** - a robust analyzer that uses JavaParser for enhanced accuracy.

### Features

- **Spring-aware** - Understands Spring Boot components (controllers, services, repositories)
- **REST API support** - Access code analysis through HTTP endpoints
- **LLM-friendly code snippets** - Extract formatted code snippets for use in AI prompts

### Quick Start

Use the provided script to quickly analyze a method's call flow:

```shell
./run-analysis.sh [ClassName] [methodName]
```

For example:
```shell
./run-analysis.sh com.sbtl1.mod1.rest.UserController getUsersAboveAge
```

The script will:
1. Build the project
2. Start the Spring Boot application with the codeanalysis profile
3. Call the flow and snippets endpoints
4. Stop the application when analysis is complete

### Using the Code Analyzer REST API

Start the Spring Boot application:

```shell
./gradlew :mod1:bootRun
```

Then access the code analysis through the REST endpoints:

* Analyze a specific method's call flow:
```shell
curl http://localhost:32000/mod1/api/codeanalysis/flow/rest/UserController/getUsersAboveAge | jq
```

* Simplified endpoint analysis:
```
curl http://localhost:32000/mod1/api/codeanalysis/endpoint/getUsersAboveAge | jq
```

* Endpoint analysis with specific controller:
```
curl http://localhost:32000/mod1/api/codeanalysis/endpoint/UserController/getUsersAboveAge | jq
```

* Get code snippets for all methods in the execution path (formatted for LLMs):
```
curl http://localhost:32000/mod1/api/codeanalysis/snippets/rest/UserController/getUsersAboveAge
```

* Simplified code snippets for an endpoint:
```
curl http://localhost:32000/mod1/api/codeanalysis/snippets/endpoint/getUsersAboveAge
```

* Code snippets for an endpoint with specific controller:
```
curl http://localhost:32000/mod1/api/codeanalysis/snippets/endpoint/UserController/getUsersAboveAge
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

### Using Code Snippets with LLMs

The code snippets endpoints provide the source code of all methods in the execution path, formatted in Markdown:

```markdown
# Code Execution Path Analysis

## Call Graph Overview

```
com.sbtl1.mod1.rest.UserController.getUsersAboveAge
  com.sbtl1.mod1.service.UserService.getUsersByAge
    com.sbtl1.mod1.dao.UserRepository.findByAgeGreaterThan
```

## Method Code Snippets

### com.sbtl1.mod1.rest.UserController.getUsersAboveAge

```java
public ResponseEntity<List<User>> getUsersAboveAge(@PathVariable int age) {
    return ResponseEntity.ok(userService.getUsersByAge(age));
}
```

### com.sbtl1.mod1.service.UserService.getUsersByAge

```java
public List<User> getUsersByAge(int age) {
    if (age < 0) {
        throw new IllegalArgumentException("Age cannot be negative");
    }
    return userRepository.findByAgeGreaterThan(age);
}
```
```

This formatted output is ideal for including in prompts for AI models when you need to explain code behavior.

### How It Works

The analyzer:
1. Locates and reads Java source files
2. Parses the source using JavaParser
3. Follows the chain of calls through the codebase
4. Builds a call graph representing the flow of execution

### Limitations

- Analysis is based on static code examination, not runtime behavior
- Complex language constructs might not be correctly analyzed
- Dynamic method calls (reflection, lambdas) are not fully supported

### Additional Documentation

For more detailed information about the code flow analyzer, see the `CALL-FLOW-ANALYZER-GUIDE.md` file in the project root.