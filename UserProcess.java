package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.security.*;
import java.io.EOFException;
import java.util.*;

public class UserProcess {
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = null;

        fileSystem = Machine.stubFileSystem();
        files = new OpenFile[maxFileDescriptor];
        filesName = new String[maxFileDescriptor];
        files[0] = UserKernel.console.openForReading();
        filesName[0] = new String("stdin");
        files[1] = UserKernel.console.openForWriting();
        filesName[1] = new String("stdout");
        for (int i = 2; i < maxFileDescriptor; ++i) {
            files[i] = null;
            filesName[i] = null;
        }

        pid = numProcess++;
    }

    public static UserProcess newUserProcess() {
        return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;
        numAliveProcess++;
        thread = new UThread(this);
        thread.setName(name).fork();
        return true;
    }

    public void saveState() {
    }

    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    private int translate(int vaddr, boolean writing) throws Exception {
        int vpn = Processor.pageFromAddress(vaddr);
        int offset = Processor.offsetFromAddress(vaddr);
        TranslationEntry entry = null;
        if (pageTable == null || vpn >= pageTable.length || pageTable[vpn] == null || !pageTable[vpn].valid) {
            throw new Exception("PageFault " + vaddr);
        }
        entry = pageTable[vpn];
        if (entry.readOnly && writing) {
            throw new Exception("ReadOnly " + vaddr);
        }
        int ppn = entry.ppn;
        if (ppn < 0 || ppn >= Machine.processor().getNumPhysPages()) {
            throw new Exception("BusError " + vaddr);
        }
        entry.used = true;
        if (writing)
            entry.dirty = true;
	int paddr = ppn * pageSize + offset;
        return paddr;
    }

    private int readMem(byte[] memory, int vaddr) throws Exception {
        int value = Lib.bytesToInt(memory, translate(vaddr, false), 1);
        return value;
    }

    private void writeMem(byte[] memory, int vaddr, int value) throws Exception {
        Lib.bytesFromInt(memory, translate(vaddr, true), 1, value);
    }

    public String readVirtualMemoryString(int vaddr, int maxLength) throws Exception {
        Lib.assertTrue(maxLength >= 0);
        byte[] bytes = new byte[maxLength+1];
        int bytesRead = readVirtualMemory(vaddr, bytes);
        for (int length=0; length<bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }
        return null;
    }

    public int readVirtualMemory(int vaddr, byte[] data) throws Exception {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) throws Exception {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
        byte[] memory = Machine.processor().getMemory();
        int amount = Math.min(length, memory.length - vaddr);
        for (int i = 0; i < amount; ++i)
            data[offset + i] = (byte)readMem(memory, vaddr + i);
        return amount;
    }

    public int writeVirtualMemory(int vaddr, byte[] data) throws Exception {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) throws Exception {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
        byte[] memory = Machine.processor().getMemory();
        int amount = Math.min(length, memory.length - vaddr);
        for (int i = 0; i < amount; ++i)
            writeMem(memory, vaddr + i, data[offset + i]);
        return amount;
    }

    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }
        try {
            coff = new Coff(executable);
        }
        catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }
        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s=0; s<coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i=0; i<args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();	

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages*pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages-1)*pageSize;
        int stringOffset = entryOffset + args.length*4;

        this.argc = args.length;
        this.argv = entryOffset;

        try {
            for (int i=0; i<argv.length; i++) {
                byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
                Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
                entryOffset += 4;
                Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
                stringOffset += argv[i].length;
                Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
                stringOffset += 1;
            }
        } catch (Throwable e) {
            return false;
        }
        return true;
    }

    protected boolean loadSections() {
        boolean ret = true;
        availablePagesLock.acquire();
        if (availablePages.size() >= numPages) {
            pageTable = new TranslationEntry[numPages];
            for (int i = 0; i < numPages; ++i) {
                pageTable[i] = new TranslationEntry(i, availablePages.remove(), true, false, false, false);
            }
        }
        else {
            ret = false;
        }
        availablePagesLock.release();
        if (!ret) {
            return false;
        }
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");
            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                int ppn = pageTable[vpn].ppn;
                section.loadPage(i, ppn);
            }
        }
        return true;
    }

    protected void unloadSections() {
        availablePagesLock.acquire();
        for (int i = 0; i < pageTable.length; ++i)
            availablePages.add(pageTable[i].ppn);
        availablePagesLock.release();
    }

    public void initRegisters() {
        Processor processor = Machine.processor();
        // by default, everything's 0
        for (int i=0; i<processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    private int handleHalt() {
        if (pid != 0)
            return 0;
        Machine.halt();
        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    private int handleExec(int fileAddr, int argc, int argvAddr) {
        String fileName = null;
        try {
            fileName = readVirtualMemoryString(fileAddr, 256);
        } catch (Throwable e) {
            return -1;
        }
        if (fileName == null) {
            return -1;
        }
        if (argc < 0) {
            return -1;
        }
        int size = argc * 4;
        byte argv[] = new byte[size];
        int numRead = 0;
        try {
            numRead = readVirtualMemory(argvAddr, argv);
        } catch (Throwable e) {
            return -1;
        }
        if (numRead != size) {
            return -1;
        }
        String arguments[] = new String[argc];
        for (int i = 0; i < argc; ++i) {
            int argumentAddr = Lib.bytesToInt(argv, i * 4);
            try {
                arguments[i] = readVirtualMemoryString(argumentAddr, 256);
            } catch (Throwable e) {
                return -1;
            }
        }
        UserProcess newProcess = newUserProcess();
        childProcesses.put(newProcess.pid, newProcess);
        newProcess.parentProcess = this;
        newProcess.execute(fileName, arguments);
        return newProcess.pid;
    }

    private int handleJoin(int pid, int statusAddr) {
        UserProcess childProcess = childProcesses.get(pid);
        if (childProcess == null)
            return -1;
	if (childProcess.thread == null)
	    return -1;
        childProcess.thread.join();

        if (childProcess.status == -1) {
            return 0;
        }
        try {
            writeVirtualMemory(statusAddr, Lib.bytesFromInt(childProcess.status));
        } catch (Throwable e) {
            handleExit(-1);
            return -2;
        }
        childProcesses.remove(pid);
        return 1;
    }

    private int handleExit(int status) {
        exitLock.acquire();
        if (parentProcess != null) {
            parentProcess.childProcesses.remove(pid);
            parentProcess = null;
        }
        this.status = status;
        for (UserProcess childProcess : childProcesses.values()) {
            childProcess.parentProcess = null;
        }
        childProcesses = null;
        for (int descriptor = 0; descriptor < files.length; descriptor++) {
            handleClose(descriptor);
        }
        unloadSections();
        if (numAliveProcess == 1)
            Kernel.kernel.terminate();
        else {
            --numAliveProcess;
            exitLock.release();
            KThread.finish();
        }
        return 0;
    }

    private int availableFileDescriptor() {
        for (int i = 0; i < maxFileDescriptor; ++i) {
            if (files[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private int handleCreateAndOpen(int nameAddr, boolean create) {
        int descriptor = availableFileDescriptor();
        if (descriptor == -1) {
            return -1;
        }
        String fileName = null;
        try {
            fileName = readVirtualMemoryString(nameAddr, 256);
        } catch (Throwable e) {
            return -1;
        }
        if (fileName == null) {
            return -1;
        }
        filesLock.acquire();
        Pair tmp = filesInfo.get(fileName);
        if (tmp != null && tmp.openTimes > 0 && tmp.unlinked) {
            filesLock.release();
            return -1;
        }
        OpenFile file = fileSystem.open(fileName, create);
        if (file == null) {
            filesLock.release();
            return -1;
        }
        files[descriptor] = file;
        filesName[descriptor] = fileName;
        if (tmp == null)
            filesInfo.put(fileName, new Pair(1, false));
        else
            filesInfo.put(fileName, new Pair(tmp.openTimes + 1, tmp.unlinked));
        filesLock.release();
        return descriptor;
    }

    private int handleCreate(int nameAddr) {
        return handleCreateAndOpen(nameAddr, true);
    }

    private int handleOpen(int nameAddr) {
        return handleCreateAndOpen(nameAddr, false);
    }

    private int handleRead(int descriptor, int bufferAddr, int count) {
        if (descriptor < 0 || descriptor >= maxFileDescriptor || files[descriptor] == null) {
            return -1;
        }
        byte buffer[] = new byte[count];
        int numRead = files[descriptor].read(buffer, 0, count);
        if (numRead == -1) {
            return -1;
        }
        int numWrite;
        try {
            numWrite = writeVirtualMemory(bufferAddr, buffer, 0, numRead);
        } catch (Throwable e) {
            return -1;
        }
        if (numWrite != numRead)
            return -1;
        return numRead;
    }

    private int handleWrite(int descriptor, int bufferAddr, int count) {
        if (descriptor < 0 || descriptor >= maxFileDescriptor || files[descriptor] == null) {
            return -1;
        }
        byte buffer[] = new byte[count];
        int numRead;
        try {
            numRead = readVirtualMemory(bufferAddr, buffer);
        } catch (Throwable e) {
            return -1;
        }
        if (numRead == -1) {
            return -1;
        }
        int numWrite = files[descriptor].write(buffer, 0, numRead);
        return numWrite;
    }

    private int handleClose(int descriptor) {
        if (descriptor < 0 || descriptor >= maxFileDescriptor || files[descriptor] == null) {
            return -1;
        }
        files[descriptor].close();
        files[descriptor] = null;
        filesLock.acquire();
        Pair tmp = filesInfo.get(filesName[descriptor]);
        if (tmp != null && tmp.openTimes == 1) {
            if (tmp.unlinked) {
                fileSystem.remove(fileName);
            }
            filesInfo.remove(filesName[descriptor]);
        }
        else {
            files.put(filesName[descriptor], new Pair(tmp.openTimes - 1, tmp.unlinked));
        }
        filesLock.release();
        filesName[descriptor] = null;
        return 0;
    }

    private int handleUnlink(int nameAddr) {
        String fileName = null;
        try {
            fileName = readVirtualMemoryString(nameAddr, 256);
        } catch (Throwable e) {
            return -1;
        }
        if (fileName == null) {
            return -1;
        }
        filesLock.acquire();
        tmp = filesInfo.get(fileName);
        if (tmp != null && tmp.openTimes > 0) {
            filesInfo.put(fileName, new Pair(tmp.openTimes, true));
            filesLock.release();
            return 0;
        }
        filesLock.release();
        boolean success = fileSystem.remove(fileName);
        if (!success) {
            return -1;
        } else {
            return 0;
        }
    }

    private static final int
        syscallHalt = 0,
        syscallExit = 1,
        syscallExec = 2,
        syscallJoin = 3,
        syscallCreate = 4,
        syscallOpen = 5,
        syscallRead = 6,
        syscallWrite = 7,
        syscallClose = 8,
        syscallUnlink = 9;

    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);
            case syscallExit:
                return handleExit(a0);
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    public void handleException(int cause) {
        Processor processor = Machine.processor();
        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                                           processor.readRegister(Processor.regA0),
                                           processor.readRegister(Processor.regA1),
                                           processor.readRegister(Processor.regA2),
                                           processor.readRegister(Processor.regA3) );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;
            default:
                handleExit(-1);
                Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    private class Pair {
        int openTimes;
        boolean unlinked;

        Pair(int o, boolean u) {
            openTimes = o;
            unlinked = u;
        }

        Pair(Pair p) {
            openTimes = p.openTimes;
            unlinked = p.unlinked;
        }
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    private static FileSystem fileSystem;
    private static final int maxFileDescriptor = 16;
    private OpenFile[] files;
    private String[] filesName;
    private static Lock filesLock = new Lock();
    private static HashMap<String, Pair> filesInfo = new HashMap<String, Pair>();

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgProcessor = 'p';

    private static ArrayDeque<Integer> availablePages;
    private static Lock availablePagesLock = new Lock();

    static {
        availablePages = new ArrayDeque<Integer>();
        int numPhysPages = Machine.processor().getNumPhysPages();
        for (int i = 0; i < numPhysPages; ++i)
            availablePages.add(i);
    }

    private UserProcess parentProcess;
    private HashMap<Integer, UserProcess> childProcesses = new HashMap<Integer, UserProcess> ();
    private int pid;
    private static int numProcess = 0;
    private int state = -1;
    private static Lock exitLock = new Lock();
    private UThread thread = null;
    private static numAliveProcess = 0;
}
