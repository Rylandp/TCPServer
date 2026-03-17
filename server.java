import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

class SimpleServer {
    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromArgs(args);
        if (config == null) {
            return;
        }

        JobRegistry registry = new JobRegistry();
        try (ServerSocket serverSocket = new ServerSocket(config.port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                Thread connection = new Thread(new ClientHandler(socket, registry, config));
                connection.start();
            }
        }
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  ClientHandler owns a single client connection. It should read one line at a
//  time, validate commands, and return exactly one response line for each
//  request. It is responsible for:
//  - Parsing the command and arguments.
//  - Enforcing usage errors and bad input errors.
//  - Delegating to the job registry to create, lookup, or cancel jobs.
//  - Returning the protocol strings (JOB, STATUS, CANCELLED, NOTCANCELLED, BYE).
//  - Keeping all protocol output deterministic and single-line.
//
class ClientHandler implements Runnable {
    private final Socket socket;
    private final JobRegistry registry;
    private final ServerConfig config;

    ClientHandler(Socket socket, JobRegistry registry, ServerConfig config) {
        this.socket = socket;
        this.registry = registry;
        this.config = config;
    }

    @Override
    public void run() {
        try{
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            
            String line;
            while((line = in.readLine()) != null){
                if(this.config.verbose) System.out.println("recv: " + line);
                String response = handle(line);
                if(this.config.verbose) System.out.println("send: " + response);
                out.println(response);

                if("BYE".equals(response)) break;
            }
        }
        catch(IOException e){
            System.out.println("error :(");
        }
        finally{
            try{
                socket.close();
            } catch(IOException e){
                System.out.println("Socket failed to close");
            }
            
        }
    }

    private String handle(String line) {
        if(line == null || line.trim().isEmpty()){
            return("ERR EMPTY");
        }

        String[] parts = line.trim().split("\\s+");
        String command = parts[0];

        if ("SUBMIT".equals(command)){
            return handleSubmit(parts.length > 1 ? parts[1] : null);
        } else if ("STATUS".equals(command)) {
            return handleStatus(parts.length > 1 ? parts[1] : null);
        } else if ("CANCEL".equals(command)) {
            return handleCancel(parts.length > 1 ? parts[1] : null);
        } else if ("QUIT".equals(command)) {
            return "BYE";
        } else {
            return usageError(command, parts.length > 1 ? parts[1] : null);
        }
    }

    private String handleSubmit(String value) {
        Long ms = parseId(value);
        if (ms == null) {
            return badMsError();
        }
        Job job = this.registry.createJob(ms);
        return "JOB " + job.id(); 
    }

    private String handleStatus(String value {
        Long id = parseId(value);
        if (id == null) {
            return badIdError();
        }
        Job job = this.registry.find(id);
        if(job == null) return "STATUS " + id + " UNKNOWN";
        return "STATUS " + id + " " + job.state();
    }

    private String handleCancel(String value) {
        Long id = parseId(value);
        if (id == null) {
            return badIdError();
        }
        Job job = this.registry.find(id);
        if(job == null) return "NOTCANCELLED " + id + " UNKNOWN";

        if(job.cancel()) return "CANCELLED " + id;
        else{
            return "NOTCANCELLED " + id + " " + job.state();
        }
    }

    private Long parseId(String value) {
        if(value == null) return null;
        try{
            return Long.parseLong(value);
        } catch(NumberFormatException e){
            return null;
        }
    }

    private String usageError(String command, String args) {
        return "ERR UNKNOWN_COMMAND: " + command + " " + args;
    }

    private String badIdError() {
        System.out.println("Security: invalid ID from " + socket.getRemoteSocketAddress());
        return "ERR BAD_ID";
    }

    private String badMsError() {
        System.out.println("Security: invalid MS from " + socket.getRemoteSocketAddress());
        return "ERR BAD_MS";
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  ServerConfig parses command-line args and holds server settings. It should:
//  - Accept an optional "-v" for verbose logging.
//  - Require a <port> argument.
//  - Print usage and return null if args are invalid.
//  - Expose the parsed port and verbose flag.
//
class ServerConfig {
    final int port;
    final boolean verbose;

    private ServerConfig(int port, boolean verbose) {
        this.port = port;
        this.verbose = verbose;
    }

    static ServerConfig fromArgs(String[] args) {
        int port = -1;
        boolean verbose = false;
        for(int arg = 0; arg < args.length; arg++){
            if(args[arg].matches("^\\d+$")){
                int portNum = Integer.parseInt(args[arg]);
                if(portNum > 1024 && portNum <= 65535) {
                    port = portNum;
                }
                else{
                    System.out.println("Invalid port! Usage: java server.java <port: 1025-65535> [-v]");
                    return null;
                }
            }
            else if(args[arg].equals("-v")){
                verbose = true;
            }
            else{
            System.out.println("Invalid port! Usage: java server.java <port: 1025-65535> [-v]");
            return null;
            }
        }
        if(port == -1) {
            System.out.println("Invalid port! Usage: java server.java <port: 1025-65535> [-v]");
            return null;
        }
        ServerConfig serverConfig = new ServerConfig(port, verbose);
        return serverConfig;
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  JobRegistry stores jobs and assigns unique ids. It should:
//  - Keep a shared map of id -> Job.
//  - Generate monotonically increasing ids (1, 2, 3, ...).
//  - Think about which methods need synchronization for thread safety.
//
class JobRegistry {
    private final Map<Long, Job> jobs = new HashMap<>();
    private long nextId = 1;

    synchronized Job createJob(long durationMs) {
        Job job = new Job(nextId, durationMs);
        jobs.put(nextId, job);
        nextId++;
        new Thread(new JobWorker(job)).start();
        return job;
    }

    synchronized Job find(long id) {
        return jobs.get(id);
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  JobWorker performs the simulated job work on its own thread and updates
//  the Job state as it starts, completes, or is cancelled.
//
//  This class is provided, but you are expected to read it and understand how it
//  drives the Job lifecycle.
//
class JobWorker implements Runnable {
    private final Job job;

    JobWorker(Job job) {
        this.job = job;
    }

    @Override
    public void run() {
        job.markRunning();

        long remaining = job.durationMs();
        long chunk = 25;
        while (remaining > 0 && !job.isCancelled()) {
            long sleepFor = Math.min(chunk, remaining);
            sleepQuietly(sleepFor);
            remaining -= sleepFor;
        }

        if (job.isCancelled()) {
            job.markCancelled();
        } else {
            job.markDone();
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  JobState provides a small set of named states that describe a job's
//  lifecycle. Think about how JobState is used as work begins, completes,
//  or is cancelled.
//
enum JobState {
    QUEUED,
    RUNNING,
    DONE,
    CANCELLED
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  Job represents a unit of work. It should:
//  - Store id and duration.
//  - Track state transitions (QUEUED -> RUNNING -> DONE or CANCELLED).
//  - Track cancellation requests and report whether cancel succeeded.
//  - Decide which methods need synchronization for thread safety.
//
class Job {
    private long id;
    private long durationMs;
    private JobState state;
    private boolean isCancelled = false;


    Job(long id, long durationMs) {
        this.id = id;
        this.durationMs = durationMs;
        this.state = JobState.QUEUED;
    }

    long id() {
        return id;
    }

    long durationMs() {
        return durationMs;
    }

    synchronized JobState state() {
        return state;
    }

    synchronized boolean isCancelled() {
        return isCancelled;
    }

    synchronized void markRunning() {
        this.state = JobState.RUNNING;
    }

    synchronized void markDone() {
        this.state = JobState.DONE;
    }

    synchronized void markCancelled() {
        this.state = JobState.CANCELLED;
    }

    synchronized boolean cancel() {
        if(state == JobState.QUEUED || state == JobState.RUNNING ) {
            this.isCancelled = true;
            return true;
        }
        return false;
    }
}
