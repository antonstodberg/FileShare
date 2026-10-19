FILESHARE
Installation och körning

1. Översikt
-----------

FileShare är ett terminalbaserat klient/server-program för att
registrera användare, ladda upp och ned filer samt dela filer
med andra registrerade användare.

Klient och server kommunicerar via SSL. Användarnas lösenord
hashas med BCrypt. MySQL lagrar användare, filmetadata och
delningsbehörigheter. Filinnehållet lagras separat på serverns disk.

Databasanslutningen använder localhost:3306 med SSL avstängt, jag
hade problem med att få det att fungera med ssl mellan server och databas efter
att ssl implementerades mellan client - server.

2. Förutsättningar
------------------

Utvecklingsmiljö:
- JDK 25
- MySQL Server 8.4.11

Följande filer ska finnas i FileShare-mappen:
- pom.xml
- setup.sql
- keystore
- truststore
- src/ med projektets källkod

Alla kommandon nedan körs från FileShare-mappen.
Klient och server startas i varsin terminal.


3. Skapa databasen
------------------

Starta MySQL Server.

Installationen förutsätter att databasen "fileshare" och kontot
"fileshare_admin"@ "localhost" inte redan finns.

Kör följande med ett MySQL-administratörskonto:

mysql -u root -p

source setup.sql;

exit;

På Linux-installationer där root använder socketautentisering kan
administratörsinloggningen i stället göras med:

sudo mysql

Kör därefter samma "source setup.sql;" och "exit;".

Installationsskriptet skapar:
- Databas: fileshare
- Tabeller: users, files, file_shares
- Databasanvändare: fileshare_admin
- Databaslösenord: 581039

Dessa inställningar motsvarar Database.java:
jdbc:mysql://localhost:3306/fileshare?sslMode=DISABLED&allowPublicKeyRetrieval=true

Om databasens namn, port, användare eller lösenord ändras måste
motsvarande inställningar i Database.java också ändras före byggning.

Databaskontot är separat från de användarkonton som registreras
inne i FileShare. Databasen är tom efter installationen.


4. Starta servern
----------------
java -Djavax.net.ssl.keyStore=keystore -Djavax.net.ssl.keyStorePassword=123456 -cp "target/classes:target/dependency/*" Server.Server

Servern använder port 2000 om ingen port anges.
En annan port kan anges sist, exempelvis:

java -Djavax.net.ssl.keyStore=keystore -Djavax.net.ssl.keyStorePassword=123456 -cp "target/classes:target/dependency/*" Server.Server 2001

MySQL måste vara tillgänglig innan servern startas.


5. Starta klienten
-----------------

Öppna en annan terminal i FileShare-mappen.

java -Djavax.net.ssl.trustStore=truststore -Djavax.net.ssl.trustStorePassword=123456 -cp "target/classes:target/dependency/*" Client.Client

Klienten ansluter till 127.0.0.1 på port 2000 som standard.

Ange en annan värd och port genom att lägga till dem sist:

java -Djavax.net.ssl.trustStore=truststore -Djavax.net.ssl.trustStorePassword=123456 -cp "target/classes:target/dependency/*" Client.Client 127.0.0.1 2001

Anges endast värden används port 2000.

Serverns keystore och klientens truststore ska användas som ett
matchande par. Filernas lösenord är 123456.


6. Alternativ: kör i IntelliJ IDEA (Jag har kört på detta sätt)
---------------------------------

Öppna projektets pom.xml som ett Maven-projekt.
Använd JDK 25 som Project SDK.

Server:
- Main class: Server.Server
- Working directory: FileShare-mappen
- VM options:
  -Djavax.net.ssl.keyStore=keystore -Djavax.net.ssl.keyStorePassword=123456
- Program arguments: tomt, eller önskad port

Klient:
- Main class: Client.Client
- Working directory: FileShare-mappen
- VM options:
  -Djavax.net.ssl.trustStore=truststore -Djavax.net.ssl.trustStorePassword=123456
- Program arguments: tomt, eller värd och eventuell port

Starta servern först och därefter klienten.


7. Filernas placering
--------------------

Servern skapar katalogen Storage under sin arbetskatalog.
Filerna lagras där med databasens fil-ID som filnamn.

Klienten skapar katalogen Downloads under sin arbetskatalog.
Om ett filnamn redan finns väljs ett namn med numeriskt suffix.

Med arbetskatalogen FileShare blir placeringarna:

FileShare/Storage/
FileShare/Downloads/

Serverns databas och Storage-katalog hör ihop. Behåll båda om
lagrade filer ska finnas kvar mellan körningar.
