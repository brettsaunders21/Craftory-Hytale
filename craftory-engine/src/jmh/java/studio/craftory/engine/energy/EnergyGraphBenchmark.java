package studio.craftory.engine.energy;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class EnergyGraphBenchmark {
    private EnergyGraph graph;

    @Setup(Level.Trial)
    public void setup() {
        graph = new EnergyGraph();
        for (int network = 0; network < 1_000; network++) {
            long baseId = network * 100L;
            for (int member = 0; member < 50; member++) {
                long id = baseId + member;
                long[] neighbours = member == 0 ? new long[0] : new long[] {id - 1};
                if (member == 0) {
                    graph.add(
                            id,
                            new MemberSpec(MemberSpec.PRODUCER, 0, 0, 10_000, 0, 5, 0, 0),
                            neighbours);
                    graph.setProduction(id, 10_000, 2);
                } else if (member % 10 == 9) {
                    graph.add(
                            id,
                            new MemberSpec(MemberSpec.LINK, 0, 0, 0, 0, 5, 0, 1_000),
                            neighbours);
                } else {
                    graph.add(
                            id,
                            new MemberSpec(MemberSpec.CONSUMER, 0, 100, 0, 0, 5, 0, 0),
                            neighbours);
                    graph.setDemand(id, 100);
                }
            }
        }
    }

    @Benchmark
    public void solveThousandNetworks() {
        graph.tick(EnergyEventSink.Companion.getNONE());
    }
}
