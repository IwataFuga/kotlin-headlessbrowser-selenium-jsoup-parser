rm ./url_config.json
kotlin -Dname="-Xmx=2048m -XX:MaxPermSize=2048m" -cp ./jars/selenium-server-standalone-3.3.1.jar:./jars/jackson-module-kotlin-2.9.0.pr3-SNAPSHOT.jar:./jars/jackson-annotations-2.9.0.pr2.jar:./jars/jackson-databind-2.1.4.jar:./jars/jackson-core-2.2.3.jar:./jars/jsoup-1.10.2.jar:./jars/jedis-3.0.0-SNAPSHOT.jar:jars/scraper.jar ScraperKt $1 $2 $3 $4 $5
