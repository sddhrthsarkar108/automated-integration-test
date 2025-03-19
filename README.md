## Springboot template with gradlew

### Pre Requisites
1. install docker & docker compose - `brew install --cask docker`
1. install `asdf` - https://asdf-vm.com/guide/getting-started.html#official-download
1. configure `asdf`
```shell
cat .tool-versions | awk '{print $1}' | xargs -I _ asdf plugin add _
asdf direnv setup --shell zsh --version latest
asdf exec direnv allow
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