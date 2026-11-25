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
