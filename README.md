# TCPServer

A multithreaded Java TCP job server with an interactive CLI client (`ManualClient`) and a multi-client load/benchmark client (`PerfClient`). Clients can submit simulated jobs with a specified duration, query job status, cancel in-flight or queued jobs, and close connections cleanly.

## Architecture & Concurrency

- **`SimpleServer` (`server.java`)**: Accepts incoming TCP connections on a configured port (`1025`–`65535`) and spawns a dedicated `Thread` running a `ClientHandler` per connection.
- **`ClientHandler`**: Reads newline-delimited commands one line at a time, validates inputs, delegates to `JobRegistry`, and writes a single deterministic response line per request.
- **`JobRegistry` & `Job`**: Maintains a shared `Map<Long, Job>` with monotonically increasing IDs (`1, 2, 3, ...`) and synchronized state/cancellation methods (`QUEUED`, `RUNNING`, `DONE`, `CANCELLED`).
- **`JobWorker`**: Executes each job on its own worker thread in `25 ms` sleep chunks, checking `job.isCancelled()` between slices so cancellation stops work promptly without waiting out the full duration.
- **`ManualClient` (`mclient.java`)**: Interactive terminal client for sending protocol commands manually.
- **`PerfClient` (`pclient.java`)**: Spawns `N` concurrent client threads, each opening a socket and submitting a fixed number of `SUBMIT <ms>` jobs.

## Requirements

- **Java 11+** (Java 11+ supports running single-file source programs directly via `java <File>.java`, or you can compile with `javac`).

## Running the Project

### 1. Start the Server

```bash
java server.java <port: 1025-65535> [-v]
```

Example (port `8080` with verbose logging):

```bash
java server.java 8080 -v
```

### 2. Run the Interactive Client

```bash
java mclient.java <host> <port>
```

Example:

```bash
java mclient.java localhost 8080
```

### 3. Run the Performance Client

```bash
java pclient.java [-v] <host> <port> <clients> <jobsPerClient> <ms>
```

Example (10 clients, 5 jobs each, 250 ms per job, verbose output):

```bash
java pclient.java -v localhost 8080 10 5 250
```

## Protocol Reference

Each request is a single line; the server replies with a single line:

| Command | Arguments | Response | Notes |
| :--- | :--- | :--- | :--- |
| `SUBMIT` | `<ms>` | `JOB <id>` or `ERR BAD_MS` | Starts a worker thread for a job lasting `<ms>` milliseconds |
| `STATUS` | `<id>` | `STATUS <id> <STATE>` or `STATUS <id> UNKNOWN` or `ERR BAD_ID` | `<STATE>` is `QUEUED`, `RUNNING`, `DONE`, or `CANCELLED` |
| `CANCEL` | `<id>` | `CANCELLED <id>` or `NOTCANCELLED <id> <STATE/UNKNOWN>` or `ERR BAD_ID` | Marks `QUEUED` or `RUNNING` jobs as cancelled |
| `QUIT` | *(none)* | `BYE` | Closes the client session |

Empty lines return `ERR EMPTY`; unrecognized commands return `ERR UNKNOWN_COMMAND: <cmd> <arg>`.

## Project Structure

```text
.
├── server.java     # SimpleServer, ClientHandler, ServerConfig, JobRegistry, JobWorker, JobState, Job
├── mclient.java    # ManualClient interactive CLI
└── pclient.java    # PerfClient concurrent load generator
```

## Notes & Known Quirks

- **Thread-per-connection & thread-per-job**: Suitable for coursework, testing, and moderate concurrency; high connection or job counts will spawn many OS threads.
- **Syntax check on `handleStatus`**: In `server.java`, `private String handleStatus(String value {` is missing its closing parenthesis `)`—fix that to `private String handleStatus(String value) {` before compiling/running.
```
