package de.openinc.api;

import java.util.List;
import org.json.JSONObject;
import de.openinc.model.user.Role;

/**
 * This interface defines the structure for administrative functions within the system. Implementing
 * classes should provide details about the function, its allowed roles, and the logic to execute
 * the function.
 */
public interface AdminFunctionInterface {

    /**
     * Retrieves the **human-readable** name of the administrative function.
     *
     * @return A string representing the name of the function.
     */
    public String getName();

    /**
     * Provides a description of the administrative function.
     *
     * @return A string containing the description of the function.
     */
    public String getDescription();

    /**
     * Retrieves the unique function name used to identify the administrative function.
     *
     * @return A string representing the unique function name.
     */
    public String getFunctionName();

    /**
     * Retrieves the list of roles that are allowed to execute this function.
     *
     * @return A list of {@link Role} objects representing the allowed roles.
     */
    public List<Role> getAllowedRoles();

    /**
     * Executes the administrative function with the provided arguments.
     *
     * @param args A {@link JSONObject} containing the arguments required for execution.
     * @return An object representing the result of the function execution.
     * @throws Exception If an error occurs during the execution of the function.
     */
    public Object execute(JSONObject args) throws Exception;
}
