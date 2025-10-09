package ttt_backend;

import ttt_backend.entities.User;

public interface UserRepoInterface {

    User addUser(final String username);

    User getUserById(final String id);

}
