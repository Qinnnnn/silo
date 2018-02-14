/*
 * Copyright  2005 PB Consult Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package de.tum.bgu.msm;

import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.container.SiloModelContainer;
import de.tum.bgu.msm.data.Dwelling;
import de.tum.bgu.msm.data.SummarizeData;
import de.tum.bgu.msm.events.EventManager;
import de.tum.bgu.msm.events.EventTypes;
import de.tum.bgu.msm.events.IssueCounter;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.transportModel.MitoTransportModel;
import de.tum.bgu.msm.transportModel.TransportModelI;
import de.tum.bgu.msm.transportModel.matsim.MatsimTransportModel;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;

import java.util.Arrays;
import java.util.List;

/**
 * @author Greg Erhardt
 * Created on Dec 2, 2009
 */
public class SiloModel {
	static Logger logger = Logger.getLogger(SiloModel.class);

	private int[] tdmYears;
	private int[] skimYears;
	private boolean trackTime;
	private long[][] timeCounter;
	private long startTime;
	private int[] scalingYears;
	private TransportModelI transportModel;
	private boolean runMatsim;
	private boolean runTravelDemandModel;

	private SiloModelContainer modelContainer;
	private SiloDataContainer dataContainer;
	private final Config matsimConfig;

	public SiloModel() {
		this(null) ;
	}

	public SiloModel(Config matsimConfig) {
		IssueCounter.setUpCounter();
		SiloUtil.modelStopper("initialize");
		this.matsimConfig = matsimConfig ;
	}

	public void runModel() {
		if (!Properties.get().main.runSilo) {
			return;
		}
		setupModel();
		runYearByYear();
		endSimulation();
	}

	private void setupModel() {
		logger.info("Setting up SILO Model (Implementation " + Properties.get().main.implementation + ")");
		setupYears();

		// create main objects and read synthetic population
		dataContainer = SiloDataContainer.createSiloDataContainer();
		if (Properties.get().main.writeSmallSynpop) {
			dataContainer.getHouseholdData().writeOutSmallSynPop();
		}
		modelContainer = SiloModelContainer.createSiloModelContainer(dataContainer);
		modelContainer.getCarOwnershipModel().initialize();

		setupTransport();
		modelContainer.getAcc().initialize();
		modelContainer.getAcc().calculateAccessibilities(Properties.get().main.startYear);

		setupTimeTracker();

		if (Properties.get().main.createPrestoSummary) {
			SummarizeData.preparePrestoSummary(dataContainer.getGeoData());
		}
	}

	private void setupTimeTracker() {
		trackTime = Properties.get().main.trackTime;
		timeCounter = new long[EventTypes.values().length + 13][Properties.get().main.endYear + 1];
		startTime = 0;
		IssueCounter.logIssues(dataContainer.getGeoData());
	}

	private void setupTransport() {
		runMatsim = Properties.get().transportModel.runMatsim;
		runTravelDemandModel = Properties.get().transportModel.runTravelDemandModel;
		if ( runMatsim && ( runTravelDemandModel || Properties.get().main.createMstmOutput)) {
			throw new RuntimeException("trying to run both MATSim and MSTM is inconsistent at this point." ) ;
		}

		if (runMatsim) {
			logger.info("  MATSim is used as the transport model");
			transportModel = new MatsimTransportModel(dataContainer, modelContainer.getAcc(), matsimConfig);
			modelContainer.getAcc().readPtSkim(Properties.get().main.startYear);
			transportModel.runTransportModel(Properties.get().main.startYear);
		} else if(runTravelDemandModel){
			logger.info("  MITO is used as the transport model");
			modelContainer.getAcc().readCarSkim(Properties.get().main.startYear);
			modelContainer.getAcc().readPtSkim(Properties.get().main.startYear);
			transportModel = new MitoTransportModel(Properties.get().main.baseDirectory, dataContainer.getGeoData(), modelContainer);
		} else {
			modelContainer.getAcc().readCarSkim(Properties.get().main.startYear);
			modelContainer.getAcc().readPtSkim(Properties.get().main.startYear);
			logger.info(" No transport model is used");
		}
	}

	private void setupYears() {
		scalingYears = Properties.get().main.scalingYears;
		if (scalingYears[0] != -1) {
			SummarizeData.readScalingYearControlTotals();
		}
		tdmYears = Properties.get().transportModel.modelYears;
		skimYears = Properties.get().transportModel.skimYears;
	}

	private void runYearByYear() {
		for (int year = Properties.get().main.startYear; year < Properties.get().main.endYear; year += Properties.get().main.simulationLength) {
			if (SiloUtil.containsElement(scalingYears, year)) {
				SummarizeData.scaleMicroDataToExogenousForecast(year, dataContainer);
			}
			logger.info("Simulating changes from year " + year + " to year " + (year + 1));
			IssueCounter.setUpCounter();    // setup issue counter for this simulation period
			SiloUtil.trackingFile("Simulating changes from year " + year + " to year " + (year + 1));
			EventManager em = new EventManager(dataContainer);

			if (trackTime) startTime = System.currentTimeMillis();
			modelContainer.getIomig().setupInOutMigration(year);
			if (trackTime) timeCounter[EventTypes.values().length][year] += System.currentTimeMillis() - startTime;

			if (trackTime) startTime = System.currentTimeMillis();
			modelContainer.getCons().planNewDwellingsForThisComingYear(year, modelContainer, dataContainer);
			if (trackTime) timeCounter[EventTypes.values().length + 1][year] += System.currentTimeMillis() - startTime;

			if (trackTime) startTime = System.currentTimeMillis();
			if (year != Properties.get().main.implementation.BASE_YEAR) {
				modelContainer.getUpdateJobs().updateJobInventoryMultiThreadedThisYear(year, dataContainer);
				dataContainer.getJobData().identifyVacantJobs();
			}
			if (trackTime) timeCounter[EventTypes.values().length + 2][year] += System.currentTimeMillis() - startTime;

			if (trackTime) startTime = System.currentTimeMillis();
			dataContainer.getHouseholdData().setUpChangeOfJob(year);   // has to run after updateJobInventoryThisYear, as updateJobInventoryThisYear may remove jobs
			if (trackTime) timeCounter[EventTypes.values().length + 3][year] += System.currentTimeMillis() - startTime;

			if (trackTime) startTime = System.currentTimeMillis();
            List<int[]> couples = modelContainer.getMardiv().selectCouplesToGetMarriedThisYear();
			if (trackTime) timeCounter[EventTypes.values().length + 5][year] += System.currentTimeMillis() - startTime;

			if (trackTime) startTime = System.currentTimeMillis();
			em.createListOfEvents(couples);
			if (trackTime) timeCounter[EventTypes.values().length + 4][year] += System.currentTimeMillis() - startTime;

			if (SiloUtil.containsElement(skimYears, year) && !SiloUtil.containsElement(tdmYears, year) &&
					!Properties.get().transportModel.runTravelDemandModel &&
					year != Properties.get().main.startYear) {
				// skims are (in non-Matsim case) always read in start year and in every year the transportation model ran. Additional
				// years to read skims may be provided in skimYears
				if (!runMatsim) {
					modelContainer.getAcc().readCarSkim(year);
				}
				modelContainer.getAcc().readPtSkim(year);
				modelContainer.getAcc().calculateAccessibilities(year);
			}

			if (trackTime) startTime = System.currentTimeMillis();
			modelContainer.getDdOverwrite().addDwellings(year, dataContainer);
			if (trackTime) timeCounter[EventTypes.values().length + 10][year] += System.currentTimeMillis() - startTime;

			if (trackTime) startTime = System.currentTimeMillis();
			modelContainer.getMove().calculateRegionalUtilities(modelContainer);
			modelContainer.getMove().calculateAverageHousingSatisfaction(modelContainer);
			if (trackTime) timeCounter[EventTypes.values().length + 6][year] += System.currentTimeMillis() - startTime;

			if (trackTime) startTime = System.currentTimeMillis();
			if (year != Properties.get().main.implementation.BASE_YEAR) dataContainer.getHouseholdData().adjustIncome();
			if (trackTime) timeCounter[EventTypes.values().length + 9][year] += System.currentTimeMillis() - startTime;

			if (trackTime) startTime = System.currentTimeMillis();
			if (year == Properties.get().main.implementation.BASE_YEAR || year != Properties.get().main.startYear)
				SiloUtil.summarizeMicroData(year, modelContainer, dataContainer);
			if (trackTime) timeCounter[EventTypes.values().length + 7][year] += System.currentTimeMillis() - startTime;

			logger.info("  Simulating events");
			// walk through all events
			for (int i = 1; i <= em.getNumberOfEvents(); i++) {
				int[] event = em.selectNextEvent();
				if (event[1] == SiloUtil.trackPp || event[1] == SiloUtil.trackHh || event[1] == SiloUtil.trackDd)
					SiloUtil.trackWriter.println ("Check event " + EventTypes.values()[event[0]] +  " for pp/hh/dd " +
							event[1]);
				if (event[0] == EventTypes.BIRTHDAY.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getBirth().celebrateBirthday(event[1]);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.CHECK_DEATH.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getDeath().chooseDeath(event[1], dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.CHECK_BIRTH.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getBirth().chooseBirth(event[1]);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.CHECK_LEAVE_PARENT_HH.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getLph().chooseLeaveParentHh(event[1], modelContainer, dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.CHECK_MARRIAGE.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
                    int[] couple = Arrays.copyOfRange(event, 1,3);
					modelContainer.getMardiv().marryCouple(couple, modelContainer, dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.CHECK_DIVORCE.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getMardiv().chooseDivorce(event[1], modelContainer, dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.CHECK_SCHOOL_UNIV.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getChangeSchoolUniv().updateSchoolUniv(event[1], dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.CHECK_DRIVERS_LICENSE.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getChangeDriversLicense().changeDriversLicense(event[1]);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.FIND_NEW_JOB.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getChangeEmployment().findNewJob(event[1], modelContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.QUIT_JOB.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getChangeEmployment().quitJob(event[1], dataContainer.getJobData());
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.HOUSEHOLD_MOVE.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getMove().chooseMove(event[1],modelContainer, dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.INMIGRATION.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getIomig().inmigrateHh(event[1], modelContainer, dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.OUT_MIGRATION.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getIomig().outMigrateHh(event[1], false, dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.DD_CHANGE_QUAL.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getRenov().checkRenovation(event[1]);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.DD_DEMOLITION.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getDemol().checkDemolition(event[1], modelContainer, dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else if (event[0] == EventTypes.DD_CONSTRUCTION.ordinal()) {
					if (trackTime) startTime = System.currentTimeMillis();
					modelContainer.getCons().buildDwelling(event[1], year, modelContainer, dataContainer);
					if (trackTime) timeCounter[event[0]][year] += System.currentTimeMillis() - startTime;
				} else {
					logger.warn("Unknown event type: " + event[0]);
				}
			}

			if (trackTime) startTime = System.currentTimeMillis();
			int[] carChangeCounter = modelContainer.getCarOwnershipModel().updateCarOwnership(dataContainer.getHouseholdData().getUpdatedHouseholds());
			dataContainer.getHouseholdData().clearUpdatedHouseholds();
			if (trackTime) timeCounter[EventTypes.values().length + 11][year] += System.currentTimeMillis() - startTime;

			//switchToYear(, year)

			if (trackTime) startTime = System.currentTimeMillis();
			int autonomousSwitchCounter = modelContainer.getSwitchToAutonomousVehicleModel().switchToAV(dataContainer.getHouseholdData().getConventionalCarsHouseholds(), year);
			dataContainer.getHouseholdData().clearConventionalCarsHouseholds();
			if (trackTime) timeCounter[EventTypes.values().length + 12][year] += System.currentTimeMillis() - startTime;

			if ( runMatsim || runTravelDemandModel || Properties.get().main.createMstmOutput) {
                if (SiloUtil.containsElement(tdmYears, year + 1)) {
                transportModel.runTransportModel(year + 1);
                    modelContainer.getAcc().calculateAccessibilities(year + 1);
                }
            }

			if (trackTime) startTime = System.currentTimeMillis();
			modelContainer.getPrm().updatedRealEstatePrices(year, dataContainer);
			if (trackTime) timeCounter[EventTypes.values().length + 8][year] += System.currentTimeMillis() - startTime;

			EventManager.logEvents(carChangeCounter, autonomousSwitchCounter);
			IssueCounter.logIssues(dataContainer.getGeoData());           // log any issues that arose during this simulation period

			logger.info("  Finished this simulation period with " + dataContainer.getHouseholdData().getNumberOfPersons() +
					" persons, " + dataContainer.getHouseholdData().getNumberOfHouseholds()+" households and "  +
					Dwelling.getDwellingCount() + " dwellings.");
			if (SiloUtil.modelStopper("check")) break;
		}
	}

	private void endSimulation() {
		if (SiloUtil.containsElement(scalingYears, Properties.get().main.endYear))
			SummarizeData.scaleMicroDataToExogenousForecast(Properties.get().main.endYear, dataContainer);

		dataContainer.getHouseholdData().summarizeHouseholdsNearMetroStations(modelContainer);

		if (Properties.get().main.endYear != 2040) {
			SummarizeData.writeOutSyntheticPopulation(Properties.get().main.endYear);
			dataContainer.getGeoData().writeOutDevelopmentCapacityFile(dataContainer);
		}

		SiloUtil.summarizeMicroData(Properties.get().main.endYear, modelContainer, dataContainer);
		SiloUtil.finish(modelContainer);
		SiloUtil.modelStopper("removeFile");
		if (trackTime) SiloUtil.writeOutTimeTracker(timeCounter);
		logger.info("Scenario results can be found in the directory scenOutput/" + Properties.get().main.scenarioName + ".");
	}


	//    private void summarizeRentAndIncome () {
	//        PrintWriter pw = SiloUtil.openFileForSequentialWriting("temp.csv", false);
	//        pw.println("income,rent");
	//        for (Household hh: Household.getHouseholdArray()) {
	//            pw.println(hh.getHhIncome() + "," + Dwelling.getDwellingFromId(hh.getDwellingId()).getPrice());
	//        }
	//        pw.close();
	//    }

	//*************************************************************************
	//*** Special implementation of same SILO Model for integration with    ***
	//*** CBLCM Framework (http://csdms.colorado.edu/wiki/Main_Page). Used  ***
	//*** in Maryland implementation only. Functions:                       ***
	//*** initialize(), runYear (double dt) and finishModel()               ***
	//*************************************************************************

	@Deprecated // use SiloModelCBLCM directly
	private SiloModelCBLCM cblcm ;
	@Deprecated // use SiloModelCBLCM directly
	public void initialize() {
		cblcm = new SiloModelCBLCM() ;
		cblcm.initialize();
	}
	@Deprecated // use SiloModelCBLCM directly
	public void runYear (double dt) {
		cblcm.runYear(dt);
	}
	@Deprecated // use SiloModelCBLCM directly
	public void finishModel() {
		cblcm.finishModel();
	}
}