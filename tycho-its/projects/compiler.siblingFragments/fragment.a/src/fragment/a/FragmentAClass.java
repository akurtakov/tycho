package fragment.a;

import host.bundle.HostClass;

public class FragmentAClass {
    public String greet() {
        return new HostClass().greet() + " from fragment A!";
    }
}
