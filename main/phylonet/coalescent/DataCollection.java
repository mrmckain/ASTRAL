package phylonet.coalescent;

import java.util.List;

import phylonet.tree.model.Tree;
import phylonet.tree.model.sti.STITreeCluster;
import phylonet.tree.model.sti.STITreeCluster.Vertex;
import phylonet.util.BitSet;

public abstract class DataCollection <T> {

	protected ClusterCollection clusters;

	protected boolean addToClusters(STITreeCluster c, int size) {
		if (size == 0) return false;
		Vertex nv = c.new Vertex();
		return clusters.addCluster(nv, size);
	}

	void addAllPossibleSubClusters(STITreeCluster cluster) {
	    int size = GlobalMaps.taxonIdentifier.taxonCount();
		BitSet bs = (BitSet) cluster.getBitSet().clone();
		bs.clear(0, size);
		while (true) {
			int tsb = bs.nextClearBit(0);
			if (tsb >= size) {
				break;
			}
			bs.set(tsb);
			bs.clear(0, tsb);
			STITreeCluster c = new STITreeCluster();
			c.setCluster((BitSet) bs.clone());
			addToClusters(c, c.getClusterSize());
		}
		System.err
				.println("Number of Clusters After Adding All possible clusters: "
						+ clusters.getClusterCount());
	}

	public abstract void addExtraBipartitionsByInput(ClusterCollection extraClusters,
			List<Tree> trees, boolean extraTreeRooted);
	
	public abstract void computeTreePartitions(Inference<T> inference);
}
