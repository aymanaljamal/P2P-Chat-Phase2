# P2P Chat - Phase 2

A distributed Peer-to-Peer (P2P) chat application developed in Java as part of the Distributed Systems course.

## Features

- Peer-to-Peer communication
- Client registration using Hadoop HDFS
- Automatic client discovery
- Real-time messaging
- Thread-safe HDFS file operations
- Inactive client cleanup
- Retry mechanism for HDFS writes

## Technologies

- Java 17+
- Maven
- Hadoop HDFS
- TCP Sockets
- Multithreading

## Project Structure

```
src/
 ├── main/
 │   ├── java/
 │   └── resources/
 └── test/
```

## Requirements

- Java JDK 17 or later
- Maven
- Hadoop HDFS

## Build

```bash
mvn clean package
```

## Run

Start Hadoop first, then run the application.

## Authors

- Ayman Aljamal
