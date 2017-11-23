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
package de.tum.bgu.msm.demography;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;

import com.pb.common.calculator.UtilityExpressionCalculator;
import com.pb.common.util.ResourceUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.container.SiloModelContainer;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.SiloUtil;
import de.tum.bgu.msm.events.EventTypes;
import de.tum.bgu.msm.events.EventManager;
import de.tum.bgu.msm.events.EventRules;
import de.tum.bgu.msm.events.IssueCounter;
import de.tum.bgu.msm.properties.Properties;
import org.apache.log4j.Logger;

import javax.script.ScriptException;

/**
 * Simulates marriage and divorce
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 31 December 2009 in Cologne
 * Revised on 5 March 2015 in Wheaton, MD
 **/

public class MarryDivorceModel {

    static Logger logger = Logger.getLogger(MarryDivorceModel.class);
    static Logger traceLogger = Logger.getLogger("trace");

    private String uecFileName;
    private int dataSheetNumber;

    private double[][] ageDependentMarryProb;
    public double[] marriageProbability;
    private static int minMarryAge;
    private float onePersonHhMarriageBias;
    private double[] divorceProbability;
    private int ageOffset;
    private ArrayList<Integer[]> couplesToMarryThisYear;

    public MarryDivorceModel() {
        uecFileName = Properties.get().main.baseDirectory + Properties.get().demographics.uecFileName;
        dataSheetNumber = Properties.get().demographics.dataSheet;

        setupMarriageModel();
        setupDivorceModel();
    }


    private void setupMarriageModel() {

        // read properties
        int marriageModelSheetNumber = Properties.get().demographics.marriageModelSheet;
        boolean logCalculation = Properties.get().demographics.logMarriageCalculation;
        minMarryAge = Properties.get().demographics.minMarryAge;
        // localMarriageAdjuster serves to adjust from national marriage rates to local conditions
        float localMarriageAdjuster = Properties.get().demographics.localMarriageAdjuster;
        onePersonHhMarriageBias = Properties.get().demographics.onePersonHhMarriageBias;

        // initialize UEC
        UtilityExpressionCalculator marriageModel = new UtilityExpressionCalculator(new File(uecFileName),
                marriageModelSheetNumber,
                dataSheetNumber,
                SiloUtil.getRbHashMap(),
                MarryDivorceDMU.class);

        MarryDivorceDMU marriageDmu = new MarryDivorceDMU();

        // everything is available
        int numAlts = marriageModel.getNumberOfAlternatives();
        int[] marryAvail = new int[numAlts+1];
        for (int i=1; i < marryAvail.length; i++) {
            marryAvail[i] = 1;
        }

        PersonType[] types = PersonType.values();
        marriageProbability = new double[types.length];
        for (int i=0; i<types.length; i++) {
            // set DMU attributes
            marriageDmu.setType(types[i]);
            // There is only one alternative, and the utility is really the probability of giving birth
            double util[] = marriageModel.solve(marriageDmu.getDmuIndexValues(), marriageDmu, marryAvail);
            marriageProbability[i] = util[0] / 2 * localMarriageAdjuster;     // "/2" because each marriage event affects two persons
            if (logCalculation) {
                // log UEC values for each person type
                marriageModel.logAnswersArray(traceLogger, "Marriage Model for Person Type " + types[i].toString());
            }
        }
        // set up probability to pick a partner by age difference
        double marryAbsAgeDiff = Properties.get().demographics.marryAbsAgeDiff;
        double marryAgeSpreadFac = Properties.get().demographics.marryAgeSpreadFac;
        ageOffset = 10;  // ageOffset is the range of ages above and below a persons age that are considered for marriage
        // needs to cover -9 to +9 to reach one person type above and one person type below
        // (e.g., for 25-old person consider partners from 20 to 34). ageOffset is 10 and not 9 to
        // capture if potential partner has celebrated birthday already (i.e. turned 35). To improve
        // performance, the person type of this person in the marriage market is not updated.
        ageDependentMarryProb = new double[2][ageOffset * 2 + 1];   // two genders and age difference classes
        for (int ageDiff = -ageOffset; ageDiff <= ageOffset; ageDiff++) {
            ageDependentMarryProb[0][ageDiff + ageOffset] =
                    1 / Math.exp(Math.pow(ageDiff + marryAbsAgeDiff, 2) * marryAgeSpreadFac);  // man searches woman
            ageDependentMarryProb[1][ageDiff + ageOffset] =
                    1 / Math.exp(Math.pow(ageDiff - marryAbsAgeDiff, 2) * marryAgeSpreadFac);  // woman searches man

        }
    }


    public int selectCouplesToGetMarriedThisYear() {
        // select singles that will get married during this coming year
        if (!EventRules.runMarriages()) return 0;
        logger.info("  Selecting couples to get married this year");
        couplesToMarryThisYear = new ArrayList<>();

        // create HashMap with men and women by age
        HashMap<String, ArrayList<Integer>> ppByAgeAndGender = new HashMap<>();

        for (Person pp: Person.getPersonArray()) {
            if (EventRules.ruleGetMarried(pp) && pp.getAge() < 100) {
                int size = Household.getHouseholdFromId(pp.getHhId()).getHhSize();
                // put only every fifth person into marriage market, emphasize single-person households
                if (size == 1 && SiloUtil.getRandomNumberAsFloat() > 0.1 * onePersonHhMarriageBias) continue;
                if (size != 1 && SiloUtil.getRandomNumberAsFloat() > 0.1) continue;
                // Store persons by age and gender
                String token = pp.getAge() + "_" + pp.getGender();
                if (ppByAgeAndGender.containsKey(token)) {
                    ArrayList<Integer> al = ppByAgeAndGender.get(token);
                    al.add(pp.getId());
                } else {
                    ppByAgeAndGender.put(token, new ArrayList<Integer>(pp.getId()));
                }
            }
        }

        // create couples
        int highestId = HouseholdDataManager.getHighestPersonIdInUse();
        boolean[] personSelectedForMarriage = SiloUtil.createArrayWithValue(highestId + 1, false);
        float interRacialMarriageShare = Properties.get().demographics.interracialMarriageShare;
        for (Person pp: Person.getPersonArray()) {
            if (EventRules.ruleGetMarried(pp) && pp.getAge() < 100 && !personSelectedForMarriage[pp.getId()]) {
                double marryProb = marriageProbability[pp.getType().ordinal()];   // raw marriage probability for this age/gender group
                // to keep things simple, emphasize prop to initialize marriage for people from single-person households. Single-person household has no influence on whether someone is selected by the marriage initializer
                if (Household.getHouseholdFromId(pp.getHhId()).getHhSize() == 1) marryProb *= onePersonHhMarriageBias;
                if (SiloUtil.getRandomNumberAsDouble() >= marryProb) continue;
                // person was selected to find a partner
                personSelectedForMarriage[pp.getId()] = true;

                // First, select interracial or monoracial marriage
                boolean sameRace = true;
                if (SiloUtil.getRandomNumberAsFloat() <= interRacialMarriageShare) sameRace = false;

                // Second, select age of new partner
                double[] ageProb = new double[ageOffset * 2 + 1];
                for (int ageDiff = -ageOffset; ageDiff <= ageOffset; ageDiff++) {
                    ageProb[ageDiff + ageOffset] = ageDependentMarryProb[pp.getGender() - 1][ageDiff + ageOffset];
                    int thisAge = pp.getAge() + ageDiff;
                    if (pp.getGender() == 1) {
                        if (ppByAgeAndGender.containsKey(thisAge + "_" + 2)) {    // man looking for women
                            ageProb[ageDiff + ageOffset] *= ppByAgeAndGender.get(thisAge + "_" + 2).size();
                        } else {
                            ageProb[ageDiff + ageOffset] = 0;
                        }
                    } else {                                                     // woman looking for men
                        if (ppByAgeAndGender.containsKey(thisAge + "_" + 1)) {
                            ageProb[ageDiff + ageOffset] *= ppByAgeAndGender.get(thisAge + "_" + 1).size();
                        } else {
                            ageProb[ageDiff + ageOffset] = 0;
                        }
                    }
                }
                if (SiloUtil.getSum(ageProb) == 0) {
                    logger.warn("Marriage market ran empty, increase share of persons. Age: "+pp.getAge());
                    break;
                }
                int selectedAge = SiloUtil.select(ageProb) - ageOffset + pp.getAge();

                // Third, select partner
                ArrayList<Integer> possiblePartners;
                if (pp.getGender() == 1) {   // Look for woman
                    possiblePartners = ppByAgeAndGender.get(selectedAge + "_" + 2);
                } else {                     // Look for man
                    possiblePartners = ppByAgeAndGender.get(selectedAge + "_" + 1);
                }
                float[] partnerProb = SiloUtil.createArrayWithValue(possiblePartners.size(), 0f);
                for (int per = 0; per < possiblePartners.size(); per++) {
                    int personId = possiblePartners.get(per);
                    if (personSelectedForMarriage[personId]) continue;  // this person was already selected to get married
                    Race personRace = Person.getPersonFromId(personId).getRace();
                    if ((sameRace && pp.getRace() == personRace) || (!sameRace && pp.getRace() != personRace)) {
                        partnerProb[per] = 10000f;
                    } else {
                        partnerProb[per] = 0.001f;  // set probability to small non-zero value to ensure that model works when marriage market runs almost empty
                    }
                }
                int selectedPartner = possiblePartners.get(SiloUtil.select(partnerProb));
                personSelectedForMarriage[selectedPartner] = true;
                couplesToMarryThisYear.add(new Integer[]{pp.getId(),selectedPartner});
                if (pp.getId() == SiloUtil.trackPp) SiloUtil.trackWriter.println("Person " + pp.getId() + " chose " +
                        "person " + selectedPartner + " to marry and they were scheduled as a couple to marry this year.");
                if (selectedPartner == SiloUtil.trackPp) SiloUtil.trackWriter.println("Person " + selectedPartner + " was chosen " +
                        "by person " + pp.getId() + " to get married and they were scheduled as a couple to marry this year.");
            }
        }
        return couplesToMarryThisYear.size();
    }


    public static int getMinMarryAge() {
        return minMarryAge;
    }


    private void setupDivorceModel() {

        // read properties
        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("DivorceCalc"));
        DivorceJSCalculator calculator = new DivorceJSCalculator(reader, false);

        // initialize results for each alternative
        PersonType[] types = PersonType.values();
        divorceProbability = new double[types.length];

        //apply the calculator to each alternative
        for (int i = 0; i < types.length; i++) {
            // set calculator bindings
            calculator.setPersonType(i);
            //calculate
            try {
                divorceProbability[i] = calculator.calculate() / 2; // each divorce event affects two persons
            } catch (ScriptException e) {
                e.printStackTrace();
            }
        }

    }


    public void choosePlannedMarriage (int coupleId, SiloModelContainer modelContainer, SiloDataContainer dataContainer) {
        // marry couple

        Integer[] personIds = couplesToMarryThisYear.get(coupleId);
        Person partner1 = Person.getPersonFromId(personIds[0]);
        if (!EventRules.ruleGetMarried(partner1)) return;  // Person got already married this simulation period or died or moved away
//        if (!EventRules.ruleGetMarried(partner1)) return true;  // Person got already married this simulation period or died or moved away
        Person partner2 = Person.getPersonFromId(personIds[1]);
        if (!EventRules.ruleGetMarried(partner2)) return;  // Person got already married this simulation period or died or moved away
//        if (!EventRules.ruleGetMarried(partner2)) return true;  // Person got already married this simulation period or died or moved away

//      Try to lock people before trying to marry them
//            if (partner1.getLock().tryLock()) {
//                try {
//                    if (partner2.getLock().tryLock()) {
//                        try {
//
//
//                            //do work
//                        } finally {
//                            partner2.getLock().unlock();
//                        }
//                    }
//                     else { //falls thru
//                        return false;
//                      }
//                } finally {
//                    partner1.getLock().unlock();
//                }
//
//
//            } else {
//
//            }
        // move partners into one household
        Household hhOfPartner1 = Household.getHouseholdFromId(partner1.getHhId());
        Household hhOfPartner2 = Household.getHouseholdFromId(partner2.getHhId());
        int moveTo = 1;
        if (partner1.getRole().equals(PersonRole.child) && !partner2.getRole().equals(PersonRole.child)) moveTo = 2; // if one is not a child, move into that household
        if (!partner1.getRole().equals(PersonRole.child) && partner2.getRole().equals(PersonRole.child)) moveTo = 1;
        if (!partner1.getRole().equals(PersonRole.child) && !partner2.getRole().equals(PersonRole.child) ||
                partner1.getRole().equals(PersonRole.child) && partner2.getRole().equals(PersonRole.child)) {
            int hhSizeA = hhOfPartner1.getHhSize();                                                                  // if both are/areNot children, move into smaller hh size
            int hhSizeB = hhOfPartner2.getHhSize();
            if (hhSizeA > hhSizeB) {
                moveTo = 2;
            } else if (hhSizeA == hhSizeB) {                                                                         // if hhSize is identical, move into larger dwelling
                Dwelling a = Dwelling.getDwellingFromId(hhOfPartner1.getDwellingId());
                Dwelling b = Dwelling.getDwellingFromId(hhOfPartner2.getDwellingId());
                if (b.getBedrooms() > a.getBedrooms()) moveTo = 2;
            }
        }
        // if household is already crowded, move couple into new household
        if ((moveTo == 1 && hhOfPartner1.getHhSize() > 3) || (moveTo == 2 && hhOfPartner2.getHhSize() > 3)) moveTo = 3;
        if (moveTo == 1) {
            // brightGroom moves to per
            hhOfPartner2.removePerson(partner2, dataContainer);
            hhOfPartner1.addAdultPerson(partner2);
            moveRemainingPersonsIfAllChildren(hhOfPartner2.getId(), hhOfPartner1, dataContainer);
            if (partner1.getId() == SiloUtil.trackPp || partner2.getId() == SiloUtil.trackPp || hhOfPartner1.getId() == SiloUtil.trackHh ||
                    hhOfPartner2.getId() == SiloUtil.trackHh) SiloUtil.trackWriter.println("Person " + partner1.getId() +
                    " and person " + partner2.getId() + " got married and moved into household " + hhOfPartner1.getId() + ".");
        } else if (moveTo == 2) {
            // per moves to brightGroom
            hhOfPartner1.removePerson(partner1, dataContainer);
            hhOfPartner2.addAdultPerson(partner1);
            moveRemainingPersonsIfAllChildren(hhOfPartner1.getId(), hhOfPartner2, dataContainer);
            if (partner1.getId() == SiloUtil.trackPp || partner2.getId() == SiloUtil.trackPp || hhOfPartner1.getId() == SiloUtil.trackHh ||
                    hhOfPartner2.getId() == SiloUtil.trackHh) SiloUtil.trackWriter.println("Person " + partner1.getId() +
                    " and person " + partner2.getId() + " got married and moved into household " + hhOfPartner2.getId() + ".");
        } else {
            // create new household for newly-wed couple
            int newHhId = HouseholdDataManager.getNextHouseholdId();
            Household newHh = new Household(newHhId, -1, -1, 2, 0);
            newHh.addPersonForInitialSetup(partner1);
            newHh.addPersonForInitialSetup(partner2);
            hhOfPartner1.removePerson(partner1, dataContainer);
            moveRemainingPersonsIfAllChildren(hhOfPartner1.getId(), newHh, dataContainer);
            hhOfPartner2.removePerson(partner2, dataContainer);
            moveRemainingPersonsIfAllChildren(hhOfPartner2.getId(), newHh, dataContainer);
            newHh.setType();
            newHh.setHouseholdRace();
            int newDwellingId = modelContainer.getMove().searchForNewDwelling(new Person[]{partner1,partner2}, modelContainer);
            if (newDwellingId < 0) {
                modelContainer.getIomig().outMigrateHh(newHhId, true, dataContainer);
                if (partner1.getId() == SiloUtil.trackPp || partner2.getId() == SiloUtil.trackPp || newHhId == SiloUtil.trackHh)
                    SiloUtil.trackWriter.println("Person " + partner1.getId() + " and person " + partner2.getId() +
                            " of household " + newHhId + " got married but could not find an appropriate vacant dwelling. " +
                            "Household outmigrated.");
                IssueCounter.countLackOfDwellingFailedMarriage();
                return;
            }
            modelContainer.getMove().moveHousehold(newHh, -1, newDwellingId, dataContainer);
        }
        partner1.setRole(PersonRole.married);
        partner2.setRole(PersonRole.married);
        EventManager.countEvent(EventTypes.checkMarriage);
        dataContainer.getHouseholdData().addHouseholdThatChanged(hhOfPartner1);
        dataContainer.getHouseholdData().addHouseholdThatChanged(hhOfPartner2);
    }


    private void moveRemainingPersonsIfAllChildren(int oldHhId, Household newHh, SiloDataContainer dataContainer) {
        // if oldHh has only children left, move children to newHh

        Household oldHh = Household.getHouseholdFromId(oldHhId);
        if (oldHh == null) return;      // oldHh was one-person household, which has married, no other persons left
        Person[] remainingPersons = oldHh.getPersons();
        boolean onlyChildren = true;
        for (Person per: remainingPersons) if (per.getRole() != PersonRole.child) onlyChildren = false;
        if (onlyChildren) {
            for (Person per: remainingPersons) {
                oldHh.removePerson(per, dataContainer);
                newHh.addAdultPerson(per);
                if (per.getId() == SiloUtil.trackPp || oldHh.getId() == SiloUtil.trackHh ||
                        newHh.getId() == SiloUtil.trackHh) SiloUtil.trackWriter.println("Person " +
                        per.getId() + " was moved from household " + oldHh.getId() + " to household " + newHh.getId() +
                        " as remaining child.");
            }
            newHh.setHouseholdRace();
            newHh.setType();
        }
    }


    public void chooseDivorce (int perId, SiloModelContainer modelContainer, SiloDataContainer dataContainer) {
        // select if person gets divorced/leaves joint dwelling

        Person per = Person.getPersonFromId(perId);
        if (!EventRules.ruleGetDivorced(per)) return;

        if (SiloUtil.getRandomNumberAsDouble() < divorceProbability[per.getType().ordinal()]) {
            // check if vacant dwelling is available
            int newDwellingId = modelContainer.getMove().searchForNewDwelling(new Person[] {per}, modelContainer);
            if (newDwellingId < 0) {
                if (perId == SiloUtil.trackPp || per.getHhId() == SiloUtil.trackHh) SiloUtil.trackWriter.println(
                        "Person " + perId + " wanted to but could not divorce from household " + per.getHhId() +
                                " because no appropriate vacant dwelling was found.");
                IssueCounter.countLackOfDwellingFailedDivorce();
                return;
            }

            // divorce
            Household oldHh = Household.getHouseholdFromId(per.getHhId());
            int toBeDivorcedId = HouseholdDataManager.findMostLikelyPartner(per, oldHh);
            Person toBeDivorced = Person.getPersonFromId(toBeDivorcedId);
            toBeDivorced.setRole(PersonRole.single);
            per.setRole(PersonRole.single);
            oldHh.removePerson(per, dataContainer);
            oldHh.setHouseholdRace();
            oldHh.setType();

            int newHhId = HouseholdDataManager.getNextHouseholdId();
            Household newHh = new Household(newHhId, -1, -1, 1, 0);
            newHh.addPersonForInitialSetup(per);
            newHh.setType();
            newHh.setHouseholdRace();
            // move divorced person into new dwelling
            modelContainer.getMove().moveHousehold(newHh, -1, newDwellingId, dataContainer);
            if (perId == SiloUtil.trackPp || newHh.getId() == SiloUtil.trackHh ||
                    oldHh.getId() == SiloUtil.trackHh) SiloUtil.trackWriter.println("Person " + perId +
                    " has divorced from household " + oldHh + " and established the new household " +
                    newHhId + ".");
            EventManager.countEvent(EventTypes.checkDivorce);
            dataContainer.getHouseholdData().addHouseholdThatChanged(oldHh);
            dataContainer.getHouseholdData().addHouseholdThatChanged(newHh);
        }
    }
}