package dev.procrun.config;

import dev.procrun.cli.RunCommand;
import dev.procrun.input.JobFileReader;
import dev.procrun.input.TsvJobFileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutowireResolverTest {
    @Test
    void constructsConcreteTypesAndResolvesNestedInterfaceDependencies() {
        var resolver = new AutowireResolver(Map.of(Dependency.class, Leaf.class));

        Root root = resolver.resolve(Root.class);

        assertInstanceOf(Leaf.class, root.branch().dependency());
    }

    @Test
    void selectsAnnotatedConstructorAmongSeveralPublicConstructors() {
        Selected instance = new AutowireResolver(Map.of()).resolve(Selected.class);

        assertNotNull(instance.leaf);
    }

    @Test
    void rejectsMultipleAnnotatedConstructors() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new AutowireResolver(Map.of()).resolve(MultipleAnnotated.class));

        assertTrue(error.getMessage().contains("Multiple constructors"));
    }

    @Test
    void rejectsAmbiguousUnannotatedConstructors() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new AutowireResolver(Map.of()).resolve(MultipleUnannotated.class));

        assertTrue(error.getMessage().contains("Expected exactly one public constructor"));
    }

    @Test
    void rejectsClassesWithoutPublicConstructors() {
        assertThrows(IllegalStateException.class,
                () -> new AutowireResolver(Map.of()).resolve(PrivateConstructor.class));
    }

    @Test
    void rejectsUnboundInterfaces() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new AutowireResolver(Map.of()).resolve(Dependency.class));

        assertTrue(error.getMessage().contains(Dependency.class.getName()));
    }

    @Test
    void rejectsIncompatibleBindingsAndNamesBothTypes() {
        var resolver = new AutowireResolver(Map.of(Dependency.class, String.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(Dependency.class));

        assertTrue(error.getMessage().contains(Dependency.class.getName()));
        assertTrue(error.getMessage().contains(String.class.getName()));
    }

    @Test
    void rejectsAbstractImplementations() {
        var resolver = new AutowireResolver(Map.of(Dependency.class, AbstractDependency.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(Dependency.class));

        assertTrue(error.getMessage().contains(AbstractDependency.class.getName()));
    }

    @Test
    void detectsCircularDependencies() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new AutowireResolver(Map.of()).resolve(CycleA.class));

        assertTrue(error.getMessage().contains("Circular dependency"));
        assertTrue(error.getMessage().contains(CycleA.class.getName()));
    }

    @Test
    void detectsCyclesThroughInterfaceBindings() {
        var resolver = new AutowireResolver(Map.of(Dependency.class, SelfDependent.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(Dependency.class));

        assertTrue(error.getMessage().contains("Circular dependency"));
        assertTrue(error.getMessage().contains(SelfDependent.class.getName()));
    }

    @Test
    void allowsRepeatedDependenciesInSeparateBranchesAndCreatesFreshInstances() {
        var resolver = new AutowireResolver(Map.of());

        Diamond first = resolver.resolve(Diamond.class);
        Diamond second = resolver.resolve(Diamond.class);

        assertNotSame(first.left().leaf(), first.right().leaf());
        assertNotSame(first, second);
        assertNotSame(first.left().leaf(), second.left().leaf());
    }

    @Test
    void preservesOriginalConstructorFailureAndAllowsLaterResolution() {
        var resolver = new AutowireResolver(Map.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(Failing.class));

        assertSame(Failing.FAILURE, error.getCause());
        assertTrue(error.getMessage().contains(Failing.class.getName()));
        assertNotNull(resolver.resolve(Leaf.class));
    }

    @Test
    void resolvesTheApplicationCommandWithItsReaderBinding() {
        var resolver = new AutowireResolver(
                Map.of(JobFileReader.class, TsvJobFileReader.class));
        CommandLine cli = new CommandLine(resolver.resolve(RunCommand.class));
        StringWriter output = new StringWriter();
        cli.setOut(new PrintWriter(output));

        assertEquals(0, cli.execute("--version"));
        assertEquals("ProcRun 0.1.0", output.toString().strip());
    }

    public interface Dependency {}

    public record Leaf() implements Dependency {}

    public record Branch(Dependency dependency) {}

    public record Root(Branch branch) {}

    public abstract static class AbstractDependency implements Dependency {}

    public record CycleA(CycleB dependency) {}

    public record CycleB(CycleA dependency) {}

    public record SelfDependent(Dependency dependency) implements Dependency {}

    public record Left(Leaf leaf) {}

    public record Right(Leaf leaf) {}

    public record Diamond(Left left, Right right) {}

    public static class Selected {
        final Leaf leaf;

        public Selected() {
            throw new AssertionError("Unannotated constructor must not be selected");
        }

        @AutowireComponent
        public Selected(Leaf leaf) {
            this.leaf = leaf;
        }
    }

    public static class MultipleAnnotated {
        @AutowireComponent
        public MultipleAnnotated() {}

        @AutowireComponent
        public MultipleAnnotated(Leaf leaf) {}
    }

    public static class MultipleUnannotated {
        public MultipleUnannotated() {}

        public MultipleUnannotated(Leaf leaf) {}
    }

    public static class PrivateConstructor {
        private PrivateConstructor() {}
    }

    public static class Failing {
        static final IllegalArgumentException FAILURE = new IllegalArgumentException("Constructor failed");

        public Failing() {
            throw FAILURE;
        }
    }
}
