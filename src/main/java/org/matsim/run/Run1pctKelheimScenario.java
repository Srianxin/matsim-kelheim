package org.matsim.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.google.common.collect.Sets;
import org.matsim.analysis.KelheimMainModeIdentifier;
import org.matsim.analysis.ModeChoiceCoverageControlerListener;
import org.matsim.analysis.personMoney.PersonMoneyEventsAnalysisModule;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.vsp.pt.fare.DistanceBasedPtFareParams;
import org.matsim.contrib.vsp.pt.fare.PtFareConfigGroup;
import org.matsim.contrib.vsp.pt.fare.PtFareModule;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controller;
import org.matsim.core.controler.ControllerUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.replanning.annealing.ReplanningAnnealerConfigGroup;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import playground.vsp.scoring.IncomeDependentUtilityOfMoneyPersonScoringParameters;

import java.util.Set;

public class Run1pctKelheimScenario {
	private static final double SAMPLE = 0.01;

	//for the new highways
	private static final double F_SPEED = 120.0 / 3.6;      // 120 km/h → m/s
	private static final double CAPACITY = 6000;            // veh/h
	private static final double LANES = 6.0;


	public static void main(String[] args) {
		// ======= Load & adapt config =======
		String configPath = "input/v3.1/kelheim-v3.1-config.xml";
		Config config = ConfigUtils.loadConfig(configPath);

		SnzActivities.addScoringParams(config);

		config.controller().setOutputDirectory("./output/output-kelheim-v3.1-1pct");
		config.plans().setInputFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/kelheim/kelheim-v3.0/input/kelheim-v3.0-1pct-plans.xml.gz");
		config.controller().setRunId("kelheim-v3.1-1pct");

		config.qsim().setFlowCapFactor(SAMPLE);
		config.qsim().setStorageCapFactor(SAMPLE);

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.abort);
		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);

		config.global().setRandomSeed(4711);

		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);

		// Relative to config
		sw.defaultParams().shp = "../shp/dilutionArea.shp";
		sw.defaultParams().mapCenter = "11.89,48.91";
		sw.defaultParams().mapZoomLevel = 11d;
		sw.sampleSize = SAMPLE;

		PtFareConfigGroup ptFareConfigGroup = ConfigUtils.addOrGetModule(config, PtFareConfigGroup.class);
		DistanceBasedPtFareParams distanceBasedPtFareParams = ConfigUtils.addOrGetModule(config, DistanceBasedPtFareParams.class);

		// Set parameters
		ptFareConfigGroup.setApplyUpperBound(true);
		ptFareConfigGroup.setUpperBoundFactor(1.5);

		// Minimum fare (e.g. short trip or 1 zone ticket)
		distanceBasedPtFareParams.setMinFare(2.0);

		distanceBasedPtFareParams.setTransactionPartner("pt-operator");
		DistanceBasedPtFareParams.DistanceClassLinearFareFunctionParams shortDistance = distanceBasedPtFareParams.getOrCreateDistanceClassFareParams(50000);
		shortDistance.setFareIntercept(1.6);
		shortDistance.setFareSlope(0.00017);

		DistanceBasedPtFareParams.DistanceClassLinearFareFunctionParams longDistance = distanceBasedPtFareParams.getOrCreateDistanceClassFareParams(Double.POSITIVE_INFINITY);
		longDistance.setFareIntercept(30);
		longDistance.setFareSlope(0.00025);
		distanceBasedPtFareParams.setOrder(1);

		ptFareConfigGroup.addParameterSet(distanceBasedPtFareParams);

		//enable plan inheritance analysis
		config.planInheritance().setEnabled(true);

		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

		// ======= Load & adapt scenario =======
		Scenario scenario = ScenarioUtils.loadScenario(config);

		//add highways
		addHighway1(scenario.getNetwork());
		addHighway2(scenario.getNetwork());
		addHighway3(scenario.getNetwork());
		addHighway4(scenario.getNetwork());
		addHighway5(scenario.getNetwork());
		addHighway6(scenario.getNetwork());
		addHighway7(scenario.getNetwork());
		addHighway8(scenario.getNetwork());
		addHighway9(scenario.getNetwork());
		addHighway10(scenario.getNetwork());
		addHighway11(scenario.getNetwork());
		addHighway12(scenario.getNetwork());


		for (Link link : scenario.getNetwork().getLinks().values()) {
			Set<String> modes = link.getAllowedModes();

			// allow freight traffic together with cars
			if (modes.contains("car")) {
				Set<String> newModes = Sets.newHashSet(modes);
				newModes.add("freight");

				link.setAllowedModes(newModes);
			}
		}

		// ======= Load & adapt controller ======
		Controller controller = ControllerUtils.createController(scenario);

		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new PtFareModule());
				install(new SwissRailRaptorModule());
				install(new PersonMoneyEventsAnalysisModule());
				install(new SimWrapperModule());

				bind(AnalysisMainModeIdentifier.class).to(KelheimMainModeIdentifier.class);
				addControlerListenerBinding().to(ModeChoiceCoverageControlerListener.class);

				//use income-dependent marginal utility of money
				bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).asEagerSingleton();
			}
		});
		controller.run();
	}

	//add highways one by one
	private static void createTwoWayLink(Network net, String idFwd, String idRev,
										 String nFrom, String nTo) {

		Node from = net.getNodes().get(Id.createNodeId(nFrom));
		Node to   = net.getNodes().get(Id.createNodeId(nTo));
		double len = NetworkUtils.getEuclideanDistance(from.getCoord(), to.getCoord());

		Link fwd = NetworkUtils.createLink(Id.createLinkId(idFwd), from, to,
			net, len, F_SPEED, CAPACITY, LANES);
		fwd.setAllowedModes(Set.of("car","freight","drt","av")); //actually only added car and freight
		net.addLink(fwd);

		Link rev = NetworkUtils.createLink(Id.createLinkId(idRev), to, from,
			net, len, F_SPEED, CAPACITY, LANES);
		rev.setAllowedModes(Set.of("car","freight","drt","av")); //same as the previous one
		net.addLink(rev);
	}

	private static void addHighway1(Network net){
		createTwoWayLink(net,"myNewHighway1","myNewHighway1Rev",
			"297315202","273092049");
	}
	private static void addHighway2(Network net){
		createTwoWayLink(net,"myNewHighway2","myNewHighway2Rev",
			"273092049","1399775825");
	}
	private static void addHighway3(Network net){
		createTwoWayLink(net,"myNewHighway3","myNewHighway3Rev",
			"1399775825","105728207");
	}
	private static void addHighway4(Network net){
		createTwoWayLink(net,"myNewHighway4","myNewHighway4Rev",
			"105728207","3447440176");
	}
	private static void addHighway5(Network net){
		createTwoWayLink(net,"myNewHighway5","myNewHighway5Rev",
			"3447440176","pt_regio_348113");
	}
	private static void addHighway6(Network net){
		createTwoWayLink(net,"myNewHighway6","myNewHighway6Rev",
			"pt_regio_348113","434482779");
	}
	private static void addHighway7(Network net){
		createTwoWayLink(net,"myNewHighway7","myNewHighway7Rev",
			"434482779","9026955992");
	}
	private static void addHighway8(Network net){
		createTwoWayLink(net,"myNewHighway8","myNewHighway8Rev",
			"9026955992","297274414");
	}
	private static void addHighway9(Network net){
		createTwoWayLink(net,"myNewHighway9","myNewHighway9Rev",
			"297274414","9057780469");
	}
	private static void addHighway10(Network net){
		createTwoWayLink(net,"myNewHighway10","myNewHighway10Rev",
			"9057780469","298138516");
	}
	private static void addHighway11(Network net){
		createTwoWayLink(net,"myNewHighway11","myNewHighway11Rev",
			"298138516","105739519");
	}
	private static void addHighway12(Network net){
		createTwoWayLink(net,"myNewHighway12","myNewHighway12Rev",
			"298138516","297315202"); //a littel mistakes here (nForm)
	}
}
