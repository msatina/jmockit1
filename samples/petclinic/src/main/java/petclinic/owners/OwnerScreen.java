package petclinic.owners;

import java.util.List;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Transactional;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An application service class that handles {@link Owner}-related operations from the owner screen.
 */
@Named
@Transactional
@ViewScoped
public class OwnerScreen {
    @Inject
    private OwnerMaintenance ownerMaintenance;
    @Nullable
    private String lastName;
    @Nullable
    private List<Owner> owners;
    @Nullable
    private Owner owner;

    @Nullable
    public String getLastName() {
        return lastName;
    }

    public void setLastName(@Nullable String lastName) {
        this.lastName = lastName;
    }

    @Nullable
    public Owner getOwner() {
        return owner;
    }

    @Nullable
    public List<Owner> getOwners() {
        return owners;
    }

    public void findOwners() {
        if (lastName == null) {
            lastName = "";
        }

        owners = ownerMaintenance.findByLastName(lastName);
    }

    public void requestNewOwner() {
        owner = new Owner();
    }

    public void selectOwner(int ownerId) {
        owner = ownerMaintenance.findById(ownerId);
    }

    public void createOrUpdateOwner() {
        assert owner != null;
        ownerMaintenance.createOrUpdate(owner);
    }
}
