## ScorePrediction (Maven Java)

A minimal Java project bootstrapped with Maven.

### Requirements
- Java 17+ (JDK)
- Maven 3.9+ installed and on your PATH

### Build
```bash
mvn -q clean package
```

### Run
Set your API key (required by the server via `API_FOOTBALL_KEY`) and start the app.

1) Set the API key environment variable:

Windows (PowerShell):
```powershell
$env:API_FOOTBALL_KEY="YOUR_API_KEY_HERE"
```

macOS/Linux (bash/zsh):
```bash
export API_FOOTBALL_KEY=YOUR_API_KEY_HERE
```

2) Option A — Run via Maven (no separate build step needed):
```bash
mvn -q exec:java
```

2) Option B — Run the packaged JAR:
```bash
mvn -q clean package
java -jar target/score-prediction-0.0.1-SNAPSHOT.jar
```

The server starts on: http://localhost:8080

Examples:
```bash
# Open the UI
start http://localhost:8080               # Windows (PowerShell)
# xdg-open http://localhost:8080          # Linux
# open http://localhost:8080              # macOS

# API calls
curl "http://localhost:8080/standings?league=39&season=2023"
curl "http://localhost:8080/predict?league=39&season=2023&team1=1&team2=2"
```

### Test
```bash
mvn -q test
```

### Project Layout
```
ScorePrediction/
  pom.xml
  src/
    main/java/com/example/scoreprediction/Main.java
    main/java/com/example/scoreprediction/WebServer.java
    main/resources/public/           # Static frontend (index.html, app.js, styles.css)
    test/java/com/example/scoreprediction/AppTest.java
```




