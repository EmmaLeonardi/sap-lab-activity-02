package ttt_backend;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import ttt_backend.entities.User;
import java.util.logging.Logger;

public class JsonDAO implements UserRepoInterface {

    /* db file */
    static final String DB_USERS = "users.json";
    private int usersIdCount;
    /* list of registered users */
    private HashMap<String, User> users;
    private static Logger logger = Logger.getLogger("[JsonDAO]");

    public JsonDAO() {
        this.usersIdCount = 0;
        this.users = new HashMap<>();
        initFromDB();
    }

    private void initFromDB() {
		try {
			var usersDB = new BufferedReader(new FileReader(DB_USERS));
			var sb = new StringBuffer();
			while (usersDB.ready()) {
				sb.append(usersDB.readLine()+"\n");
			}
			usersDB.close();
			var array = new JsonArray(sb.toString());
			for (int i = 0; i < array.size(); i++) {
				var user = array.getJsonObject(i);
				var key = user.getString("userId");
				this.users.put(key, new User(key, user.getString("userName")));
				usersIdCount++;
			}
			
		} catch (Exception ex) {
			// ex.printStackTrace();
			logger.info("No dbase, creating a new one");
			saveOnDB();
		}
	}
	
	private void saveOnDB() {
		try {
			JsonArray list = new JsonArray();
			for (User u: users.values()) {
				var obj = new JsonObject();
				obj.put("userId", u.id());
				obj.put("userName", u.name());
				list.add(obj);
			}
			var usersDB = new FileWriter(DB_USERS);
			usersDB.append(list.encodePrettily());
			usersDB.flush();
			usersDB.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}	
	}

    @Override
    public User addUser(String username) {
        var newUserId = "user-"+usersIdCount;
		var user = new User(newUserId, username);
		users.put(newUserId, user);
		saveOnDB();
        usersIdCount++;
        return user;
    }

    @Override
    public User getUserById(String id) {
        return this.users.get(id);
    }

}
