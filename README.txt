FileShare

Jag har kört projektet i IntelliJ med JDK 25, Maven och MySQL 8.4.11.
Använd FileShare som arbetskatalog (mappen med pom.xml och keystore).

Databas
Kör setup.sql en gång med ett MySQL-adminkonto:
mysql -u root -p < setup.sql

Skriptet skapar databasen fileshare och kontot fileshare_admin och förutsätter
att de inte redan finns. Servern ansluter till MySQL på localhost:3306.

Körning i IntelliJ
Öppna pom.xml som Maven-projekt. Starta Server.Server först och sedan
Client.Client. Lägg följande under VM options för respektive körning:

Server.Server:
-Djavax.net.ssl.keyStore=keystore -Djavax.net.ssl.keyStorePassword=123456

Client.Client:
-Djavax.net.ssl.trustStore=truststore -Djavax.net.ssl.trustStorePassword=123456

Inga programargument behövs för lokal körning. Klienten ansluter till
127.0.0.1:2000. Registrera en användare i klienten för att testa.
