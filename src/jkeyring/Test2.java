import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION;
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES;
import com.sun.jna.platform.win32.WinBase.STARTUPINFO;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.ptr.IntByReference;


public class Test2 {
    static final int HANDLE_FLAG_INHERIT = 0x00000001;
    static final int BUFSIZE = 4096;
    static final int STARTF_USESTDHANDLES = 0x00000100;

    public static void main(String[] args) {
	if (args.length < 1) {
	      System.err.println("Please specify a command.\n");
	      System.exit(1);
	}

	SECURITY_ATTRIBUTES saAttr = new SECURITY_ATTRIBUTES();
	saAttr.dwLength = new DWORD(saAttr.size());
	saAttr.bInheritHandle = true;
	saAttr.lpSecurityDescriptor = null;

	// Create a pipe for the child process's STDIN. 
	HANDLEByReference childStdInRead = new HANDLEByReference();
	HANDLEByReference childStdInWrite = new HANDLEByReference();
	createPipe(saAttr, childStdInRead, childStdInWrite);

	// Ensure the read handle to the pipe for STDERR is not inherited.
	HANDLEByReference childStdErrRead = new HANDLEByReference();
	HANDLEByReference childStdErrWrite = new HANDLEByReference();
	createPipe(saAttr, childStdErrRead, childStdErrWrite);

	// Create a pipe for the child process's STDOUT. 
	HANDLEByReference childStdOutRead = new HANDLEByReference();
	HANDLEByReference childStdOutWrite = new HANDLEByReference();
	createPipe(saAttr, childStdOutRead, childStdOutWrite);

	// Create the child process. 
	String szCmdline = args[0];
	PROCESS_INFORMATION processInformation = new PROCESS_INFORMATION();
	STARTUPINFO startupInfo = new STARTUPINFO();
	startupInfo.cb = new DWORD(processInformation.size());
	startupInfo.hStdError = childStdErrWrite.getValue();
	startupInfo.hStdOutput = childStdOutWrite.getValue();
	startupInfo.hStdInput = childStdInRead.getValue();
	startupInfo.dwFlags |= STARTF_USESTDHANDLES;
	if (Kernel32.INSTANCE.CreateProcess(
		null, 
		szCmdline, 
		null, 
		null, 
		true, 
		new DWORD(0x00000020), 
		null, 
		null, 
		startupInfo, 
		processInformation)) {
	    Kernel32.INSTANCE.WaitForSingleObject(processInformation.hProcess, 0xFFFFFFFF);
	    Kernel32.INSTANCE.CloseHandle(processInformation.hProcess);
	    Kernel32.INSTANCE.CloseHandle(processInformation.hThread);
	} else {
	    System.err.println(Kernel32.INSTANCE.GetLastError());
	}

	HandleCopier stdoutCopier = new HandleCopier(childStdOutRead, System.out);
	stdoutCopier.start();
	HandleCopier stderrCopier = new HandleCopier(childStdErrRead, System.err);
	stderrCopier.start();

	IntByReference dwWritten = new IntByReference();
	int len = 0;
	byte[] buff = new byte[1024];
	try {
	    while (stdoutCopier.isEOF() && (len = System.in.read(buff)) > 0) {
		if (!Kernel32.INSTANCE.WriteFile(childStdInWrite.getValue(), buff, len, dwWritten, null)) {
		    break;
		}
	    } 
	} catch (IOException e) {
	    e.printStackTrace();
	} 

/*
	// The remaining open handles are cleaned up when this process terminates. 
	// To avoid resource leaks in a larger application, close handles explicitly. 
	if (!Kernel32.INSTANCE.CloseHandle(childStdInWrite.getValue())){ 
	    System.err.println(Kernel32.INSTANCE.GetLastError()); 
	}
*/
    }

    /**
     * Create a pipe for the child process's STDOUT and ensure the read handle to the pipe for STDOUT is not inherited.
     */
    static void createPipe(SECURITY_ATTRIBUTES saAttr, HANDLEByReference read, HANDLEByReference write) {
	if (!Kernel32.INSTANCE.CreatePipe(read, write, saAttr, 0)){
	    System.err.println(Kernel32.INSTANCE.GetLastError());
	}
	if (!Kernel32.INSTANCE.SetHandleInformation(read.getValue(), HANDLE_FLAG_INHERIT, 0)){
	    System.err.println(Kernel32.INSTANCE.GetLastError());;
	}
    }

    static class HandleCopier implements Runnable {
	private Thread thread = null;
	private boolean EOF = false;
	private HANDLEByReference handle;
	private OutputStream out;
	private IntByReference dwRead, dwWritten;
	private ByteBuffer buf;
	private Pointer data;

	HandleCopier(HANDLEByReference handle, OutputStream out) {
	    this.handle = handle;
	    this.out = out;
	    dwRead = new IntByReference();
	    dwWritten = new IntByReference(); 
	    buf = ByteBuffer.allocateDirect(BUFSIZE);
	    data = Native.getDirectBufferPointer(buf);
	}

	void start() {
	    if (thread == null) {
		EOF = false;
		thread = new Thread(this);
		thread.start();
	    } else {
		throw new IllegalThreadStateException("started");
	    }
	}

	boolean isEOF() {
	    return EOF;
	}

	// Implement Runnable

	public void run() {
	    try {
		while(true) {
		    if (Kernel32.INSTANCE.ReadFile(handle.getValue(), buf.array(), BUFSIZE, dwRead, null)) {
			out.write(data.getByteArray(0, dwRead.getValue()));
			out.flush();
		    } else {
			break;
		    }
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    EOF = true;
	}
    }
}
