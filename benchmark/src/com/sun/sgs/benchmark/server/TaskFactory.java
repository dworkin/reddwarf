package com.sun.sgs.benchmark.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.Task;

import com.sun.sgs.benchmark.app.BehaviorModule;

import java.io.Serializable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class TaskFactory implements ManagedObject, Serializable {

    private static final long serialVersionUID = 0x109109FFFL;

    /**
     * The reference to singleton instance of this class, which is
     * managed by the Datastore.
     */
    private static final String FACTORY_NAME_REF = "0x01928891L";
    
    /**
     * The prefix for all benchmark methods in the application
     * properties file
     */
    private static final String PREFIX = "com.sun.sgs.benchmark.";

    private static final String PREOPS_STR  = "preops";
    private static final String OPS_STR     = "ops";
    private static final String POSTOPS_STR = "postops";

    /**
     * The mapping from op codes to the tasks needed to execute the
     * method specified by the op code.
     */
    private final Map<byte[], MethodTaskProfile> opCodes;

    /**
     * The mapping from method name to the tasks needed to execute a
     * specific method
     */
    private final Map<String, MethodTaskProfile> methods;

    private TaskFactory() {
	opCodes = new HashMap<byte[], MethodTaskProfile>();
	methods = new HashMap<String, MethodTaskProfile>();
    }

    /**
     * Returns an ordered list of {@code Runnable} operations that
     * reflect this specific behavior based on method name and
     * arguments.  These operations include those that should be run
     * before, during and after the desired operations.
     *
     * @param session      the client session on whose behalf this
     *                     method is invoked
     * @param methodOpCode the op-codes for the method to invoke
     * @param args         arguments to this method encoded as op-codes
     *
     * @return the ordered lists of {@code Runnable} operations that
     *         represent this method
     */
    public List<Runnable> getOperations(ClientSession session, 
					byte[] methodOpCode, 
					byte[] argOpCodes) {
	MethodTaskProfile b = opCodes.get(methodOpCode);
	if (b != null) {
	    return b.getOperations(session, argOpCodes);
	}
	else {
	    // log that we didn't have something to generate any task
	    // and return an empty list
	    return new LinkedList<Runnable>();
	}
    }

    /**
     * Returns an ordered list of {@code Runnable} operations that
     * reflect this specific behavior based on method name and
     * arguments.  These operations include those that should be run
     * before, during and after the desired operations.
     *
     * @param session    the client session on whose behalf this method
     *                   is invoked
     * @param methodName the name of the method to invoke
     * @param args       arguments to this method encoded as op-codes
     *
     * @return the ordered lists of {@code Runnable} operations that
     *         represent this method
     */
    public List<Runnable> getOperations(ClientSession session, 
					String methodName, 
					Object[] args) {
	MethodTaskProfile b = methods.get(methodName);
	if (b != null) {
// 	    System.out.printf("loaded profile for %s(): %s\n", 
// 			      methodName, b);
	    return b.getOperations(session, args);
	}	
	else {
	    // log that we didn't have something to generate any task
	    // and return an empty list
	    System.out.printf("no profile for method %s\n", methodName);
	    return new LinkedList<Runnable>();
	}	
    }    
	
    /**
     * Returns the singleton instance of the {@code TaskFactory}, or
     * if it does not exist, creates the singleton instance of this
     * object and registers with the {@link
     * com.sun.sgs.app.DataManager}.
     *
     * @return the singleton instance
     */
    public static TaskFactory instance() {
	TaskFactory tf = null;
	try {
	    tf = AppContext.getDataManager().getBinding(FACTORY_NAME_REF,
							TaskFactory.class);	    
	}
	catch (NameNotBoundException nnbe) {
	    tf = new TaskFactory();
	    AppContext.getDataManager().setBinding(FACTORY_NAME_REF, tf);
	}
	return tf;
    }

    /**
     * Parses the properties keys and then registers the specified
     * {@code BehaviorModule}s for their respective method.  Modules
     * are specified in the application properties file as such:
     *
     * <pre>
     * com.sun.sgs.benchmark.<type>.<name>.<order>=<class[,...]>
     * </pre>
     *
     * where {@code type} is either {@code method} or {@code opcode}
     * to specifed how the method is represented.  {@code name} should
     * be the {@code String} encoded version of the name.  {@code
     * order} specifies the operation order for these modules as
     * either {@code preops}, {@code ops}, or {@code postops}.  Each
     * key should be mapped to a comma-delineated list of
     * fully-qualified class names for each {@code BehavorModule} to
     * load.
     */
    void loadModules(Properties appProperties) {
	// mark the as needing to write
	AppContext.getDataManager().markForUpdate(this);

	// we need java 1.6 for this:
	//for (String key : appProperties.stringPropertyNames()) {
	    
	//for (Object o : appProperties.propertyNames()) {
	Enumeration<?> enumer = appProperties.propertyNames();
	while (enumer.hasMoreElements()) {
	    
	    Object o = enumer.nextElement();
	
	    if (!(o instanceof String))
		continue;

	    String key = (String)o;

	    // check that the prefix is what we expect
	    if (!key.startsWith(PREFIX)) {
		// REMINDER: add logging
		System.out.printf("unrecognized prefix for key: %s\n",
				  key);
		continue;
	    }
		
	    // split the rest of the key into three substrings:
	    // representation, name, operation order
	    String[] typeNameAndOrder = 
		key.substring(PREFIX.length()).split("\\.");

	    if (typeNameAndOrder.length != 3) {
		// REMINDER: add logging
		System.out.printf("invalid key format: %s\n", key);
		continue;
	    }

	    // reference out the individual elements for clarity
	    String type = typeNameAndOrder[0];
	    String name = typeNameAndOrder[1];
	    String order = typeNameAndOrder[2];

	    MethodTaskProfile profile = null;

	    if (type.equals("method")) {
		profile = methods.get(name);
		if (profile == null) {
		    profile = new MethodTaskProfile();
		    methods.put(name, profile);
		}		
	    }
	    else if (type.equals("opcode")) {
		profile = opCodes.get(name);
		if (profile == null) {
		    profile = new MethodTaskProfile();
		    opCodes.put(name.getBytes(), profile);
		}
	    }
	    else {
		// REMINDER: add logging
		System.out.printf("unknown method representation code: %s\n",
			      type);
		continue;
	    }
	    
	    
	    String[] behaviorModuleNames = 
		appProperties.getProperty(key).split(",");
   
	    for (String className : behaviorModuleNames) {
		BehaviorModule module  = loadModule(className);
		if (module == null) { // unsuccessful load
		    // REMINDER: add logging
		    continue;
		}
		addModule(profile, module, order);
	    }
	}
    }

    /**
     * Returns an instance of the specified class name.
     *
     * @param className the name of the {@code BehaviorModule} class
     * @return an instance of the specified class name.
     */
    private BehaviorModule loadModule(String className) {
	BehaviorModule mod = null;
	try {
	    Class<?> clazz = Class.forName(className);
	    Constructor<?> c = clazz.getConstructor(new Class<?>[]{});
	    mod = (BehaviorModule)(c.newInstance(new Object[]{}));
	    System.out.printf("loaded module: %s\n", mod);
	}
	// REMINDER: put in logging for these
	catch (NoSuchMethodException nsme) {
	    System.out.printf("class does not have no-arg constructor");
	}
	catch (ClassNotFoundException cnfe) {
	    System.out.printf("unrecognized class: %s\n", className);
	}
	catch (InstantiationException ie) {
	    ie.printStackTrace();
	}
	catch (IllegalAccessException iae) {
	    iae.printStackTrace();
	}
	catch (InvocationTargetException ite) {
	    ite.printStackTrace();
	}
	return mod;
    }

    /**
     * Adds the specified module to the method profile at the desired
     * execution order.
     *
     * @param profile the profile to which the operation should be
     *                added
     * @param module  the operation to perform
     * @param opOrder when the operation should be performed
     */
    private void addModule(MethodTaskProfile profile, 
			   BehaviorModule module, String opOrder) {
	if (opOrder.equals(PREOPS_STR)) {
	    profile.addPreop(module);
	}
	else if (opOrder.equals(OPS_STR)) {
	    profile.addOp(module);
	}
	else if (opOrder.equals(POSTOPS_STR)) {
	    profile.addPostop(module);
	}
	else {
	    // unknown op type
	    System.out.printf("unknown operation type: \"%s\". " +
			      "Unable to load module.\n", opOrder);
	}
    }
    

    /**
     * A {@code MethodTaskProfile} is responsible for generating the
     * ordered list of operations that must occur on behalf of a
     * method request before, during, and after its execution.  These
     * each operation is generated by a {@link
     * com.sun.sgs.benchmark.app.BehaviorModule} that is inserted in
     * the operation-generation chain.  For a given method (either in
     * op-code or {@code Object} form), the arguments are used to
     * determine which specific behavior should be done for the
     * method.
     */
    private static class MethodTaskProfile implements Serializable {

	private static final long serialVersionUID = 1010101010101L;

	/**
	 * The generators for tasks that should be scheduled <i>before</i>
	 * the main part of the operation.
	 */
	private final List<BehaviorModule> preops;

	/**
	 * The generators for tasks that should be scheduled <i>as</i>
	 * the main part of the operation.
	 */
	private final List<BehaviorModule> ops;

	/**
	 * The generators for tasks that should be scheduled <i>after</i>
	 * the main part of the operation.
	 */
	private final List<BehaviorModule> postops;
	
	public MethodTaskProfile() {
	    // use ArrayLists for faster iteration, since the add case
	    // is rare
	    preops  = new ArrayList<BehaviorModule>();
	    ops     = new ArrayList<BehaviorModule>();
	    postops = new ArrayList<BehaviorModule>();
	}

	/**
	 * Registers a new {@code BehaviorModule} to generated a list
	 * of {@link Runnable} operations that should be performed
	 * before the main operation of this behavior.
	 */
	public void addPreop(BehaviorModule mod) {
	    preops.add(mod);
	}

	/**
	 * Registers a new {@code BehaviorModule} to generated a list
	 * of {@link Runnable} operations that should be performed as
	 * the main operation of this behavior.
	 */
	public void addOp(BehaviorModule mod) {
	    ops.add(mod);
	}

	/**
	 * Registers a new {@code BehaviorModule} to generated a list
	 * of {@link Runnable} operations that should be performed after
	 * the main operation of this behavior.
	 */
	public void addPostop(BehaviorModule mod) {
	    postops.add(mod);
	}

	/**
	 * Returns an ordered list of {@code Runnable} operations that
	 * reflect this specific behavior based on the op-code
	 * arguments.  
	 *
	 * @param session the client session on whose behalf this
	 *                method is invoked
	 * @param args    arguments to this method encoded as op-codes
	 *
	 * @return the ordered lists of {@code Runnable}s to be
	 *         executed
	 */
	public List<Runnable> getOperations(ClientSession session, 
					    byte[] args) {
	    LinkedList<Runnable> tasks = new LinkedList<Runnable>();
	    for (BehaviorModule mod : preops) 
		tasks.addAll(mod.getOperations(session, args));
	    for (BehaviorModule mod : ops) 
		tasks.addAll(mod.getOperations(session, args));
	    for (BehaviorModule mod : postops) 
		tasks.addAll(mod.getOperations(session, args));
	    return tasks;
	}

	/**
	 * Returns an ordered list of {@code Runnable} operations that
	 * reflect this specific behavior based on the arguments.
	 *
	 * @param session the client session on whose behalf this
	 *                method is invoked
	 * @param args    {@code Object} arguments to this method.
	 *
	 * @return the ordered lists of {@code Runnable}s to
	 *         be executed
	 */
	public List<Runnable> getOperations(ClientSession session, 
					    Object[] args) {
	    LinkedList<Runnable> tasks = new LinkedList<Runnable>();
	    for (BehaviorModule mod : preops) 
		tasks.addAll(mod.getOperations(session, args));
	    for (BehaviorModule mod : ops) 
		tasks.addAll(mod.getOperations(session, args));
	    for (BehaviorModule mod : postops) 
		tasks.addAll(mod.getOperations(session, args));
	    return tasks;
	}

	public String toString() {
	    return "MethodTaskProfile [pre-ops: " + preops
		+ ", ops: " + ops + ", post-ops" + postops + "]";
		
	}
    }

}