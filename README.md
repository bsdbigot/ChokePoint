# ChokePoint

## Description
ChokePoint is designed to be a poor man's Samhain or TripWire, for use in limited-access environments, such as cPanel on a shared server.

## Requirements
1. JDK 1.7 to compile
2. JRE 1.7 or better to run

## Usage
### Setup
1. Create ~/.chokepoint/ directory
2. Install JAR and log4j2.xml to ~/.chokepoint/
3. Adjust path in log4j2.xml
   - Configuration/Appenders/File.fileName
3. Add cron entry, for example:
   - 0 2 * * * /usr/bin/java -Dlog4j.configurationFile=~/.chokepoint/log4j2.xml -jar ~/.chokepoint/ChokePoint-0.0.1-SNAPSHOT-jar-with-dependencies.jar ~/.chokepoint/MyWatchedFolder ~/MyWatchedFolder
4. Optionally add MAILTO=your.email@ESP to your crontab
5. Watch for daily emails (or whatever cadence you specified in cron), or review the log, periodically.

### Customization
1. ~/.chokepoint/ is the recommended installation, however you may change this
2. log4j2.xml can be tweaked to suit your needs
   - Configuration/Appenders/File.fileName
   - Configuration/Appenders/Console/PatternLayout.pattern
