package phylonet.coalescent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import phylonet.tree.model.Tree;
import phylonet.tree.model.sti.STITreeCluster;
import phylonet.tree.model.sti.STITreeCluster.Vertex;

public abstract class ComputeMinCostTask<T> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 244989909835073096L;
	Inference<T> inference;
	Vertex v;
	ClusterCollection clusters;

	// final int maxEL = 10000000;
	ClusterCollection containedVertecies;

	protected Double compute() {
		try {
			return computeMinCost();
		} catch (CannotResolveException e) {
			return null;
		}
	}

	public ComputeMinCostTask(Inference<T> inference, Vertex v, 
			ClusterCollection clusters) {
		this.inference = inference;
		this.v = v;
		this.clusters = clusters;
	}

	private void addComplementaryClusters(int clusterSize) {
		Iterator<Set<Vertex>> it = containedVertecies.getSubClusters().iterator();
		while (it.hasNext()) {
			Collection<Vertex> subClusters = new ArrayList<Vertex>(it.next());
			int i = -1;
			for (Vertex x : subClusters) {
				i = i > 0 ? i : x.getCluster().getClusterSize();
				int complementarySize = clusterSize - i;
				containedVertecies.addCluster(
						getCompleteryVertx(x, v.getCluster()),
						complementarySize);
			}
			if (i < clusterSize * inference.getCD()) {
				return;
			}

		}
	}

	private double computeMinCost() throws CannotResolveException {


		// -2 is used to indicate it cannot be resolved
		if (v._done == 2) {
			throw new CannotResolveException(v.getCluster().toString());
		}
		// Already calculated. Don't re-calculate.
		if (v._done == 1) {
			return v._max_score;
		}
		//

		int clusterSize = v.getCluster().getClusterSize();

		// SIA: base case for singelton clusters.
		if (clusterSize <= 1) {
			
			v._max_score = scoreBaseCase(inference.rooted, inference.trees);
			
			v._min_lc = (v._min_rc = null);
			v._done = 1;
			return v._max_score;
		}

		boolean tryAnotherTime = false;

		containedVertecies = clusters.getContainedClusters(v.getCluster());

		do {
			tryAnotherTime = false;

			if (clusterSize >= GlobalMaps.taxonIdentifier.taxonCount() * inference.getCS()) {
				addComplementaryClusters(clusterSize);
			}
			Collection<STBipartition> clusterResolutions = containedVertecies
					.getClusterResolutions();
			
			int clusterLevelCost = 0;
			if (!clusterResolutions.isEmpty()) {
				clusterLevelCost = calculateClusterLevelCost();
			}
			/*
			 * System.out.println("xL: "+this.v.getCluster() + " "+xl + " "+
			 * DeepCoalescencesCounter
			 * .getClusterCoalNum(this.inference.trees, this.v.getCluster(),
			 * taxonNameMap, true));
			 */
			for (STBipartition bi : clusterResolutions) {
				try {
					Vertex smallV = containedVertecies.getVertexForCluster(bi.cluster1);
					Vertex bigv = containedVertecies.getVertexForCluster(bi.cluster2);
					ComputeMinCostTask<T> smallWork = newMinCostTask(
							smallV, containedVertecies);
					ComputeMinCostTask<T> bigWork = newMinCostTask(
							bigv, containedVertecies);
					CalculateWeightTask<T> weigthWork = null;

					// MP_VERSION: smallWork.fork();
					Double rscore = bigWork.compute();

					if (rscore == null) {
						// MP_VERSION: weigthWork.cancel(false);
						// MP_VERSION: smallWork.cancel(false);
						throw new CannotResolveException(bigv.getCluster()
								.toString());
					}

					Double lscore;
					// MP_VERSION: lscore = smallWork.join();
					lscore = smallWork.compute();

					if (lscore == null) {
						// MP_VERSION: weigthWork.cancel(false);
						throw new CannotResolveException(smallV
								.getCluster().toString());
					}
					// MP_VERSION: w = weigthWork.join();

		
					Long weight = null;
					if (clusterSize == GlobalMaps.taxonIdentifier.taxonCount()) {
						weight = defaultWeightForFullClusters();
					}
					
					if (weight == null) {
						T t = STB2T(bi);					
						weight =  inference.weightCalculator.getWeight(t, this);
						//System.out.print(weight/Integer.MAX_VALUE);
					}					
					
					double c = adjustWeight(clusterLevelCost, smallV, bigv, weight);					
//					double l = 2.4;
//					if (clusterSize > 5 && v._max_score > l*(lscore + rscore))
//						if ((lscore + rscore + c)> v._max_score)
					//System.err.println(clusterSize+"\tmissing " +(lscore + rscore + c)+"\t"+v._max_score+"\t"+(lscore + rscore + c)/v._max_score);
					
					if ((v._max_score != -1)
							&& (lscore + rscore + c < v._max_score)) {
						continue;
					}
					v._max_score = (lscore + rscore + c);
					v._min_lc = smallV;
					v._min_rc = bigv;
					v._c = c;
					
				} catch (CannotResolveException c) {
					// System.err.println("Warn: cannot resolve: " +
					// c.getMessage());
				}
			}
			if (v._min_lc == null || v._min_rc == null) {
				if (clusterSize <= 5) {
					addAllPossibleSubClusters(v.getCluster(), containedVertecies);
					tryAnotherTime = true;
				} else if (clusterSize > 1) {
					// System.err.println(maxSubClusters);
					Iterator<Set<Vertex>> it = containedVertecies.getSubClusters().iterator();
					if (it.hasNext()) {
						Collection<Vertex> biggestSubClusters = new ArrayList<Vertex>(it.next());
						int i = -1;
						for (Vertex x : biggestSubClusters) {
							i = i > 0 ? i : x.getCluster().getClusterSize();
							int complementarySize = clusterSize - i;
							if (complementarySize > 1) {
								tryAnotherTime |= containedVertecies
										.addCluster( getCompleteryVertx(x,v.getCluster()), complementarySize);
							}
						}
						/*
						 * if (tryAnotherTime && clusterSize > 10) {
						 * System.err .println("Adding up to " +
						 * biggestSubClusters.size()+" extra |"+i+
						 * "| clusters (complementary of included clusters) for size "
						 * + clusterSize + " : " + v.getCluster()+"\n" +
						 * containedVertecies.getClusterCount()); }
						 */
					}

				}
			}
		} while (tryAnotherTime);


		if (v._min_lc == null || v._min_rc == null) {
			System.err.println("WARN: No Resolution found for ( "
					+ v.getCluster().getClusterSize() + " taxa ):\n"
					+ v.getCluster());
			v._done = 2;
			throw new CannotResolveException(v.getCluster().toString());
		}
		/*
		 * if (clusterSize > 450){
		 * System.out.println(v+" \nis scored "+(v._max_score ) +
		 * " by \n"+v._min_lc + " \n"+v._min_rc); }
		 *//*
			 * if (clusterSize > 5){ counter.addGoodSTB(bestSTB, clusterSize); }
			 */
		v._done = 1;
		return v._max_score;
	}

	abstract Long defaultWeightForFullClusters();

	protected abstract ComputeMinCostTask<T> newMinCostTask(Vertex v, 
	ClusterCollection clusters);

	abstract protected double adjustWeight(int clusterLevelCost, Vertex smallV,
			Vertex bigv, Long Wdom);

    abstract protected int calculateClusterLevelCost();
	
	abstract protected int scoreBaseCase(boolean rooted, List<Tree> trees);
	
	abstract protected T STB2T(STBipartition stb);

	void addAllPossibleSubClusters(STITreeCluster cluster,
			ClusterCollection containedVertecies) {
		int size = cluster.getClusterSize();
		for (int i = cluster.getBitSet().nextSetBit(0); i >= 0; i = cluster
				.getBitSet().nextSetBit(i + 1)) {
			STITreeCluster c = new STITreeCluster(cluster);
			c.getBitSet().clear(i);

			Vertex nv = c.new Vertex();
			containedVertecies.addCluster(nv, size - 1);

			addAllPossibleSubClusters(c, containedVertecies);
		}
	}

	public Vertex getCompleteryVertx(Vertex x, STITreeCluster refCluster) {
		STITreeCluster c = x.getCluster();

		STITreeCluster revcluster = new STITreeCluster(refCluster);
		revcluster.getBitSet().xor(c.getBitSet());
		Vertex reverse = revcluster.new Vertex();
		// int size = reverse._cluster.getClusterSize();
		return reverse;
	}

	/*
	 * public boolean addAllPossibleSubClusters(STITreeCluster cluster) { int
	 * size = cluster.getClusterSize(); boolean ret = false; for (int i =
	 * cluster.getCluster().nextSetBit(0); i>=0 ;i =
	 * cluster.getCluster().nextSetBit(i+1)){ STITreeCluster c = new
	 * STITreeCluster(cluster); c.getCluster().clear(i); ret |= addToClusters(c,
	 * size-1); ret |= addAllPossibleSubClusters(c); } return ret; }
	 */

	/*
	 * public boolean addAllPossibleSubClusters(STITreeCluster cluster) { int
	 * size = cluster.getClusterSize(); boolean ret = false; for (int i =
	 * cluster.getCluster().nextSetBit(0); i>=0 ;i =
	 * cluster.getCluster().nextSetBit(i+1)){ STITreeCluster c = new
	 * STITreeCluster(cluster); c.getCluster().clear(i); ret |= addToClusters(c,
	 * size-1); ret |= addAllPossibleSubClusters(c); } return ret; }
	 */

}
