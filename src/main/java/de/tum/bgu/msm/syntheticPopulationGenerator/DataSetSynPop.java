package de.tum.bgu.msm.syntheticPopulationGenerator;


import com.pb.common.datafile.TableDataSet;
import com.pb.common.matrix.Matrix;
import de.tum.bgu.msm.data.DwellingType;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Ana Moreno on 29.11.2017. Adapted from MITO
 */


public class DataSetSynPop {

    private static final Logger logger = Logger.getLogger(DataSetSynPop.class);

    private TableDataSet microDataPersons;
    private TableDataSet microDataHouseholds;
    private TableDataSet microDataDwellings;

    private TableDataSet weights;
    private TableDataSet frequencyMatrix;
    private TableDataSet errorsCounty;
    private TableDataSet errorsMunicipality;
    private TableDataSet errorsSummary;
    private TableDataSet errorsBorough;
    private Map<String, int[]> valuesByHousehold;

    private HashMap<Integer, ArrayList> municipalitiesByCounty;
    private HashMap<Integer, int[]> tazByMunicipality;
    private ArrayList<Integer> municipalities;
    private ArrayList<Integer> boroughs;
    private ArrayList<Integer> counties;
    private int[] cityIDs;
    private int[] countyIDs;
    private Map<Integer, Map<Integer, Float>> probabilityZone;
    private Map<Integer, Map<DwellingType, Integer>> dwellingPriceByTypeAndZone;

    private Matrix distanceTazToTaz;

    private HashMap<Integer, ArrayList> boroughsByCity;

    private Map<Integer, Map<String, Integer>> households;
    private Map<Integer, Map<String, Integer>> persons;
    private Map<Integer, Map<String, Integer>> dwellings;



    public TableDataSet getMicroDataPersons() {
        return microDataPersons;
    }

    public void setMicroDataPersons(TableDataSet microDataPersons) {
        this.microDataPersons = microDataPersons;
    }

    public TableDataSet getMicroDataHouseholds() {
        return microDataHouseholds;
    }

    public void setMicroDataHouseholds(TableDataSet microDataHouseholds) {
        this.microDataHouseholds = microDataHouseholds;
    }

    public TableDataSet getMicroDataDwellings() {
        return microDataDwellings;
    }

    public void setMicroDataDwellings(TableDataSet microDataDwellings) {
        this.microDataDwellings = microDataDwellings;
    }

    public TableDataSet getWeights() {
        return weights;
    }

    public void setWeights(TableDataSet weights) {
        this.weights = weights;
    }

    public TableDataSet getFrequencyMatrix() {
        return frequencyMatrix;
    }

    public void setFrequencyMatrix(TableDataSet frequencyMatrix) {
        this.frequencyMatrix = frequencyMatrix;
    }

    public HashMap<Integer, ArrayList> getMunicipalitiesByCounty() {
        return municipalitiesByCounty;
    }

    public void setMunicipalitiesByCounty(HashMap<Integer, ArrayList> municipalitiesByCounty) {
        this.municipalitiesByCounty = municipalitiesByCounty;
    }

    public HashMap<Integer, int[]> getTazByMunicipality() {
        return tazByMunicipality;
    }

    public void setTazByMunicipality(HashMap<Integer, int[]> tazByMunicipality) {
        this.tazByMunicipality = tazByMunicipality;
    }

    public Matrix getDistanceTazToTaz() {
        return distanceTazToTaz;
    }

    public void setDistanceTazToTaz(Matrix distanceTazToTaz) {
        this.distanceTazToTaz = distanceTazToTaz;
    }

    public int[] getCityIDs() {
        return cityIDs;
    }

    public void setCityIDs(int[] cityIDs) {
        this.cityIDs = cityIDs;
    }

    public int[] getCountyIDs() {
        return countyIDs;
    }

    public void setCountyIDs(int[] countyIDs) {
        this.countyIDs = countyIDs;
    }

    public ArrayList<Integer> getMunicipalities() {
        return municipalities;
    }

    public void setMunicipalities(ArrayList<Integer> municipalities) {
        this.municipalities = municipalities;
    }

    public ArrayList<Integer> getCounties() {
        return counties;
    }

    public void setCounties(ArrayList<Integer> counties) {
        this.counties = counties;
    }

    public TableDataSet getErrorsCounty() {
        return errorsCounty;
    }

    public void setErrorsCounty(TableDataSet errorsCounty) {
        this.errorsCounty = errorsCounty;
    }

    public TableDataSet getErrorsMunicipality() {
        return errorsMunicipality;
    }

    public void setErrorsMunicipality(TableDataSet errorsMunicipality) {
        this.errorsMunicipality = errorsMunicipality;
    }

    public TableDataSet getErrorsSummary() {
        return errorsSummary;
    }

    public void setErrorsSummary(TableDataSet errorsSummary) {
        this.errorsSummary = errorsSummary;
    }

    public Map<String, int[]> getValuesByHousehold() {
        return valuesByHousehold;
    }

    public void setValuesByHousehold(Map<String, int[]> valuesByHousehold) {
        this.valuesByHousehold = valuesByHousehold;
    }

    public Map<Integer, Map<Integer, Float>> getProbabilityZone() {
        return probabilityZone;
    }

    public void setProbabilityZone(Map<Integer, Map<Integer, Float>> probabilityZone) {
        this.probabilityZone = probabilityZone;
    }

    public HashMap<Integer, ArrayList> getBoroughsByCity() {
        return boroughsByCity;
    }

    public void setBoroughsByCity(HashMap<Integer, ArrayList> boroughsByCity) {
        this.boroughsByCity = boroughsByCity;
    }

    public TableDataSet getErrorsBorough() {
        return errorsBorough;
    }

    public void setErrorsBorough(TableDataSet errorsBorough) {
        this.errorsBorough = errorsBorough;
    }

    public ArrayList<Integer> getBoroughs() {
        return boroughs;
    }

    public void setBoroughs(ArrayList<Integer> boroughs) {
        this.boroughs = boroughs;
    }

    public Map<Integer, Map<String, Integer>> getHouseholds() {
        return households;
    }

    public void setHouseholds(Map<Integer, Map<String, Integer>> households) {
        this.households = households;
    }

    public Map<Integer, Map<String, Integer>> getPersons() {
        return persons;
    }

    public void setPersons(Map<Integer, Map<String, Integer>> persons) {
        this.persons = persons;
    }

    public Map<Integer, Map<String, Integer>> getDwellings() {
        return dwellings;
    }

    public void setDwellings(Map<Integer, Map<String, Integer>> dwellings) {
        this.dwellings = dwellings;
    }

    public Map<Integer, Map<DwellingType, Integer>> getDwellingPriceByTypeAndZone() {
        return dwellingPriceByTypeAndZone;
    }

    public void setDwellingPriceByTypeAndZone(Map<Integer, Map<DwellingType, Integer>> dwellingPriceByTypeAndZone) {
        this.dwellingPriceByTypeAndZone = dwellingPriceByTypeAndZone;
    }
}