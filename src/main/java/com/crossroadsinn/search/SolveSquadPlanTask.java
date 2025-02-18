package com.crossroadsinn.search;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import com.crossroadsinn.problem.SquadPlan;
import com.crossroadsinn.signups.Player;
import com.crossroadsinn.settings.Squads;
import com.crossroadsinn.settings.Squad;

import java.util.*;
import java.util.ArrayList;

/**
 * Solves Squad Generation with any commander list,
 * player list and search algorithm.
 * @author Eren Bole.8720
 * @version 1.0
 */
public class SolveSquadPlanTask extends Task<SquadPlan> {

    private final ArrayList<Player> commanders, trainees;
    private final int maxSquads;
    private final ObservableList<SquadPlan> results;

    // We basically search for a maximum of X seconds for the SMALL_RESULT_SIZE
    // If we find a lot of solutions very fast, we can go up to MAX_RESULT but only up to MAX_SEARCH_DURATION_SECONDS_SMALL_RESULT_SIZE
    private final int MAX_SEARCH_DURATION_SECONDS = 30;
    private final int MAX_SEARCH_DURATION_SECONDS_SMALL_RESULT_SIZE = 1;
    public static final int MAX_RESULT = 1000;
    private final int SMALL_RESULT_SIZE = 50;
    private int minHeuristic = Integer.MAX_VALUE;

    public SolveSquadPlanTask(ArrayList<Player> commanders, ArrayList<Player> trainees, int maxSquads, ListChangeListener<SquadPlan> resultListener) {
        this.maxSquads = maxSquads;
        this.commanders = commanders;
        this.trainees = trainees;
        this.results = FXCollections.observableArrayList();
        // reset all assigned roles
        this.commanders.forEach(p -> {
            p.resetAssignedRole();
            // Make sure to flag all trainers as trainer
            p.setTrainer(true);
        });
        this.results.addListener(resultListener);
        this.trainees.forEach(Player::resetAssignedRole);
    }

    /**
     * Generate Squads given a list of trainees and trainers and a SearchAlgorithm.
     * The search algorithm is a greedy depth first, however there are often multiple solutions to the problem but we don't want to calculate ALL the possiblities
     * So what we do is, we call the algorithm, which shuffles the input and dives in depth first.
     * It will try to find a solution in an orderly fashion, this guarantees it will try every possible combination until it finds a solution
     * If this takes too long, it will fail and stop. Most cases however it will find something quite fast
     * But, because it is greedy, this might be a solution that we'd rather not have (for example commanders on special roles)
     * To prevent calculation EVERYTHING and still get a reasonable result, we simply call the algorithm X times, because it shuffles the input, it will probably branch out in X different ways
     * This way we get reasonable solution in a short amount of time
     * @return The solution state if any.
     */
    protected SquadPlan call() {
        // Get the squaType allowed
        String squadType = Squads.getSquads().stream().filter(s -> s.getEnabled().getValue()).findFirst().map(Squad::getSquadHandle).orElse("default");

        SquadPlan squadPlanState = new SquadPlan(trainees, commanders, maxSquads, squadType) ;
        int numSquads = squadPlanState.getNumSquads();
        boolean isDone = false;

        System.out.println("Solving for " + numSquads + " squads... for up to " + MAX_RESULT + " results or max " + MAX_SEARCH_DURATION_SECONDS + " seconds");
        long startTime = System.currentTimeMillis();
        while (!isDone) {
            if (isCancelled()) return null;

            SquadPlan solution = null;
            try {
                solution = squadPlanState.expandOrReturnSolution();
            } catch (Exception e) {
                // Failed to find, try again
            }
            if (solution == null) {
                System.out.println("Too many failures, need to start of differently, current results: " + results.size());
            } else {
                minHeuristic = Integer.min(solution.heuristic(), minHeuristic);
            }

            // Sometimes we just find the perfect setup, all squads 5 dps and no commanders on special roles
            if (minHeuristic == numSquads * 5) {
                System.out.println("Found perfect solution, stop search and use it!");
                return solution;
            }

            long endTime = System.currentTimeMillis();
            double searchDuration = (endTime-startTime) / 1000.0;
            // In case we failed to find a solution and failed before we have to try again with a lower squadcount
            if (searchDuration > MAX_SEARCH_DURATION_SECONDS && results.size() == 0) {
                System.out.println("Failed in: " + searchDuration + " seconds. Attempting again with a lower amount of squads");
                --numSquads;
                if (numSquads == 0) {
                    isDone = true;
                } else {
                    startTime = System.currentTimeMillis();
                    squadPlanState = new SquadPlan(trainees, commanders, numSquads, squadType);
                }
            } else {
                if (solution != null) {
                    results.add(solution);
                }
                // if we have enough results (or took too long) just pick the best one, lowest heuristic
                boolean smallBatchAcceptable = results.size() >= SMALL_RESULT_SIZE && searchDuration >= MAX_SEARCH_DURATION_SECONDS_SMALL_RESULT_SIZE;
                if (results.size() >= MAX_RESULT || searchDuration > MAX_SEARCH_DURATION_SECONDS || smallBatchAcceptable) {
                    System.out.println("Successful in: " + searchDuration + " seconds and " + results.size() + " results.");
                    return results.stream().min(Comparator.comparingInt(SquadPlan::heuristic)).get();
                }
                squadPlanState = new SquadPlan(trainees, commanders, numSquads, squadType);
            }
        }
        return null;
    }
}
