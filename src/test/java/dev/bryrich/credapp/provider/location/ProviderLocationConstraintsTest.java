package dev.bryrich.credapp.provider.location;

import dev.bryrich.credapp.common.UserGroupTestSupport;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.location.GroupLocation;
import dev.bryrich.credapp.group.membership.GroupProvider;
import dev.bryrich.credapp.provider.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * provider_locations carries group_id so two composite foreign keys can enforce that the
 * location and the provider belong to the same group. These prove that holds in the
 * database, not just in the service that checks first.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ProviderLocationConstraintsTest extends UserGroupTestSupport {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProviderLocationRepository providerLocationRepository;

    private Group group;
    private GroupLocation location;
    private Provider member;
    private Provider outsider;

    @BeforeEach
    void setUp() {
        group = entityManager.persistAndFlush(new Group("Northside Health", "123456789"));

        location = new GroupLocation("Main Clinic", "123 Main St");
        location.setGroup(group);
        location = entityManager.persistAndFlush(location);

        member = entityManager.persistAndFlush(new Provider("Ada", "Byron"));
        outsider = entityManager.persistAndFlush(new Provider("Grace", "Hopper"));

        entityManager.persistAndFlush(new GroupProvider(group, member, null));
    }

    @Test
    void aProviderInTheGroupCanBePlacedAtItsLocation() {
        providerLocationRepository.save(new ProviderLocation(location, member, PcpScp.PCP));

        entityManager.flush();
        entityManager.clear();

        assertThat(providerLocationRepository.findByProviderId(member.getId()))
                .singleElement()
                .satisfies(pl -> {
                    assertThat(pl.getPcpScp()).isEqualTo(PcpScp.PCP);
                    assertThat(pl.getGroupId()).isEqualTo(group.getId());
                });
    }

    @Test
    void aProviderOutsideTheGroupCannotBePlacedAtItsLocation() {
        ProviderLocation placement = new ProviderLocation(location, outsider, PcpScp.SCP);

        assertThatThrownBy(() -> providerLocationRepository.saveAndFlush(placement))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removingTheProviderFromTheGroupTakesTheirPlacementsWithIt() {
        providerLocationRepository.save(new ProviderLocation(location, member, PcpScp.PCP));
        entityManager.flush();

        GroupProvider membership = entityManager.getEntityManager()
                .createQuery("SELECT gp FROM GroupProvider gp WHERE gp.provider.id = :id",
                        GroupProvider.class)
                .setParameter("id", member.getId())
                .getSingleResult();
        entityManager.remove(membership);
        entityManager.flush();
        entityManager.clear();

        assertThat(providerLocationRepository.findByProviderId(member.getId())).isEmpty();
    }
}
