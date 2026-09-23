package dev.bryrich.credapp.usergroup;

import jakarta.persistence.*;
import java.util.UUID;

/** An account workspace, separate from the medical practices in the groups table. */
@Entity
@Table(name = "user_groups")
public class UserGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String joinCode;

    protected UserGroup() {}

    public UserGroup(String name) {
        this.name = name.trim();
        this.joinCode = UUID.randomUUID().toString();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getJoinCode() { return joinCode; }
}
