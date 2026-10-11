

/**
 * Macro for data evaluation of RIDES1
 * data on PhotosynQ.org
 * by: David M. Kramer
 * created: 2017-05-09 @ 18:15:27
 */

// Note: the following variables are for fitting the ECS decay curves when the
// procedure written by Kevin and Greg often fails. Fortunately, Sebastian added
// a really important new feature that allows for very flexible and useful
// nonliear curve fitting.

// Define the output object here
var output = {}; //dictionary to hold results
// output.n_sets=json.set.length;

// Define the protocol set numbers for each data set
// var ECS_LEFdset=2;
// var P700_DIRK_set=3;
// var PAM_set=4;  //the json set 8 contains the PAM and P700 traces
// var SPAD_set=8;
// var calib_set=8;

// var labels=[];
// for (var i=1; i < output.n_sets; i++) {
//   var temp=json.set[i].label;
//   // labels.push(temp);
//   // if (temp=="DIRK_ECS"){
//   // 	output.tt1=temp;
//   //   ECS_LEFdset=i;
//   // }
//   if (temp=="DIRK_P700"){
//   	output.tt2=temp;
//     P700_DIRK_set=i;
//   }
//   if (temp=="PAM"){
//   	output.tt3=temp;
//     PAM_set=i;
//   }

// }

DIRK_ECS = GetProtocolByLabel("DIRK_ECS", json);
P700_DIRK = GetProtocolByLabel("DIRK_P700", json);
PAM = GetProtocolByLabel("PAM", json);

output["leaf angle"] = P700_DIRK.angle;

// return output;

// output.labels=labels;
// output.t=json.set[1].label;

if (json.set.length==13){
	  json.set.unshift({});
}

//output.label=json.set[2].label;




// ECS DIRK trace definitions
var beginning_of_ECS=100; //Note the 100 pulses
var length_of_ECS_subtrace=220; //The length of each subtrace.
var number_of_ECS_subtraces=6; // number of ECS subtraces. There are currently 6 of them.
var length_of_ECS_baseline=100;  //there are 100 points in the baseline before the DIRK
var length_of_ECS_all_subtraces=beginning_of_ECS+(number_of_ECS_subtraces*length_of_ECS_subtrace);
var ECS_averaged_trace=DIRK_ECS.data_raw.slice(beginning_of_ECS,320);
var begining_of_subtrace_for_linear_fit=75;
var end_of_subtrace_for_linear_fit=145;

var P700_begining_of_subtrace_for_linear_fit=55;
var P700_end_of_subtrace_for_linear_fit=170;

var beginning_of_LEFD=1421; //Note the 100 pulses
var length_of_LEFd_subtrace=220; //The length of each subtrace.
var length_of_LEFd_baseline=100;  //there are 100 points in the baseline before the DIRK
var LEFd_trace=DIRK_ECS.data_raw.slice(beginning_of_LEFD,beginning_of_LEFD+length_of_LEFd_subtrace);

// P700 DIRK trace definitions
var beginning_of_P700_DIRK=100; //Note the 100 pulses
var length_of_P700_DIRK_subtrace=220; //The length of each subtrace.
var number_of_P700_DIRK_subtraces=6; // number of ECS subtraces. There are currently 6 of them.
var length_of_P700_DIRK_baseline=100;  //there are 100 points in the baseline before the DIRK
var length_of_P700_DIRK_all_subtraces=beginning_of_P700_DIRK+(number_of_P700_DIRK_subtraces*length_of_P700_DIRK_subtrace);
var P700_DIRK_averaged_trace=DIRK_ECS.data_raw.slice(beginning_of_P700_DIRK,320);


//reshape the PAM and P700 traces from alterrnating 1,2,1,2... to 1,1,1,1...2,2,2,2...

output.test_data_raw_PAM = PAM.data_raw.length;
var loPAM= PAM.data_raw.length;

var temp1= [];
for (var i=0; i<loPAM; i=i+2){
	temp1.push(PAM.data_raw[i]);
}

for (var i=1; i<loPAM+1; i=i+2){
	temp1.push(PAM.data_raw[i]);
}

PAM.data_raw=temp1; //replace the old values wiht temp1

// PAM fluorescence traces
//DEFINE THE STARTING AND ENDING POINTS TO USE FOR THE VARIOUS
//PARAMETERS USED IN THE CALCULATIONS

var Fs_begin=1; //the first point to use for Fs
var Fs_end =4; //the end point to use  for Fs

//The Fv/FM' results will be calculated using hte Avenson technique, wherein
//a series of different saturation pulse intensities are used and the
//true saturation point is inferred by extrapolation to infinite
//intensities.

//The first FM value (Fm_1) is obtained with the highest intensity
var Fm_1_begin =101; //the first point to use for the FIRST Fmp
var Fm_1_end =130; //the end point to use  for the FIRST Fmp
var Fm_2_begin =131; //the first point to use for the FIRST Fmp
var Fm_2_end =145; //the end point to use  for the FIRST Fmp
var Fm_3_begin =146; //the first point to use for the FIRST Fmp
var Fm_3_end =160; //the end point to use  for the FIRST Fmp
var Fm_4_begin =161; //the first point to use for the FIRST Fmp
var Fm_4_end =175; //the end point to use  for the FIRST Fmp
var Fm_5_begin =176; //the first point to use for the FIRST Fmp
var Fm_5_end =190; //the end point to use  for the FIRST Fmp

//START AND STOP FOR F0'
var FoPrime_begin =206; //the first point to use for Fo
var FoPrime_end =220; //the end point to use  for Fo

//SET THE INVERSE INTENSITIES FOR THE AVENSON INTENSITY RAMP
var inverse_intensity = [1/8000,1/7000,1/6000,1/5000];

output.pump="none";

//PSI saturation pulse parameters:
//Calculation of the DIRK delta_A_ECS

var PSI_ss_beg=1; //beginning of the trace for P700 steady-state
var PSI_ss_end=18; //end of the trace for P700 steady-state
var PSI_sat1_beg=25; //beginning of the trace for P700 first saturation pulse
var PSI_sat1_end=170; //end of the trace for P700 first saturation pulse
var PSI_dark_beg=195; //beginning of the trace for P700 steady-state
var PSI_dark_end=200; //end of the trace for P700 steady-state
var PSI_sat2_beg=220; //beginning of the trace for P700 second saturation pulse
var PSI_sat2_end=270; //end of the trace for P700 second saturation pulse


// Start of analyses sections

//ECS trace analysis:

var i, j;
  for (i = 1; i < number_of_ECS_subtraces; i++) {
      var temp=DIRK_ECS.data_raw.slice(i*length_of_ECS_subtrace+beginning_of_ECS,
                                         (i+1)*length_of_ECS_subtrace+beginning_of_ECS);
		for (j = 0; j < length_of_ECS_subtrace; j++) {
    		ECS_averaged_trace[j]=ECS_averaged_trace[j]+temp[j];
    }}

//make up a time axis. the assumption is that all the points are equally spaced in time.
   var fake_time_axis = [];
	for (var i = 1; i <= length_of_ECS_subtrace; i++) {
      ii=i.toFixed(4);
   	fake_time_axis.push(i);
  }
  //Find the best fit line to the baseline points.
  	var reg=MathLINREG(fake_time_axis.slice(begining_of_subtrace_for_linear_fit, end_of_subtrace_for_linear_fit),ECS_averaged_trace.slice(begining_of_subtrace_for_linear_fit, end_of_subtrace_for_linear_fit));

  //Generate a line based on the baseline linear regression over the range of values for the full trace.
  var baseline_offset=[];
  for (j = 0; j < length_of_ECS_subtrace; j++) {
    	jj=j.toFixed(4);
		baseline_offset.push(jj*MathROUND(reg.m, 3) + MathROUND(reg.b));
  }

  //output.baseline_offset=baseline_offset;
   //Calculate the deltaI/I0 and convertto approximate delta_A
	for (j = 0; j < length_of_ECS_subtrace; j++) {
    var rat= ECS_averaged_trace[j]/baseline_offset[j];
	ECS_averaged_trace[j]=-1*MathLOG(rat); //((rat-1)/-2.3);
    }
ECS_averaged_trace[120]=ECS_averaged_trace[119]; //eliminate the spike artifact at end of trace
output.ECS_averaged_trace=ECS_averaged_trace.slice(80,150);

var begin_trace_index=100;
var end_trace_index=120;
var number_of_points_to_fit=end_trace_index-begin_trace_index;
var expData=ECS_averaged_trace.slice(begin_trace_index,end_trace_index);

// Here I assume that the time difference between points was

var time_per_point=1.5; //entger the delta time between points (only work with constant delta time)

// Obtain best fit results for ECS decay using non-linear least squares fitting
var tdata=[];
for (i in expData) {
	tdata.push([i*time_per_point, expData[i]]);
}

var a = 1;
var b = 1;
var c = 1;
try{
	var fit = NonLinearRegression(tdata,{
	   equation: 'b + a * e(- x / c)',
	   initial: [ a, b, c ]
	});

	a= fit.parameters[0].value;
	b= fit.parameters[1].value;
	c = fit.parameters[2].value;

	var outdata=[];
	for (i in expData) {
		outdata.push(b + a*Math.exp(-1*i/c));
	}

	output.fitinput=expData;
	output.outdata=outdata;

	// Available for all parameters
	//output.p1_name = fit.parameters[0].name;
	output["ECSt mAU"] = MathROUND(fit.parameters[0].value, 5);
	//output.offset = fit.parameters[1].value;
	output["ECS_tau"] = MathROUND(0.001*fit.parameters[2].value, 4);
	output["gH+"] = MathROUND(1000/fit.parameters[2].value, 3);
	var vHplus = output["ECSt mAU"] *  output["gH+"];
	output["vH+"] = MathROUND(vHplus, 3);

}
catch(e){}


//***********other possible parameters
//output.p1_sd = fit.parameters[0].sd_error;
//output.p1_p = fit.parameters[0].p;
//output.iterations = fit.iterations;
// Some more info as text
//output.ParameterEstimates = fit.ParameterEstimates;
//output.CovarianceMatrix = fit.CovarianceMatrix;
//output.expFitArray=expFitArray;
//expReg = MathEXPINVREG(expFitArray);
//var ECS_lifetime_ms=expReg.lifetime; //the fitting procedure returns -99999 if it fails to find converge


//Calculaiton of the DIRK delta_P850

  //Get sum of all subtraces
  var i, j;
  for (i = 1; i < number_of_P700_DIRK_subtraces; i++) {
      var temp=P700_DIRK.data_raw.slice(i*length_of_P700_DIRK_subtrace+beginning_of_P700_DIRK,
                                         (i+1)*length_of_P700_DIRK_subtrace+beginning_of_P700_DIRK);

		for (j = 0; j < length_of_P700_DIRK_subtrace; j++) {
    		P700_DIRK_averaged_trace[j]=P700_DIRK_averaged_trace[j]+temp[j];
    }
  }

  //make up a time axis. the assumption is that all the points are equally spaced in time.
   var fake_time_axis = [];
	for (var i = 1; i <= length_of_P700_DIRK_subtrace; i++) {
      ii=i.toFixed(4);
   	fake_time_axis.push(i);
  }
  //Find the best fit line to the baseline points.
  	var reg=MathLINREG(fake_time_axis,P700_DIRK_averaged_trace);

  //Generate a line based on the baseline linear regression over the range of values for the full trace.
  var baseline_offset=[];
  for (j = 0; j < length_of_P700_DIRK_subtrace; j++) {
    	jj=j.toFixed(4);
		baseline_offset.push(jj*MathROUND(reg.m, 3) + MathROUND(reg.b));
  }

  //output.baseline_offset=baseline_offset;
   //Calculate the deltaI/I0 and convertto approximate delta_A
	for (j = 0; j < length_of_P700_DIRK_subtrace; j++) {
    var rat= P700_DIRK_averaged_trace[j]/baseline_offset[j];

	P700_DIRK_averaged_trace[j]=-1*MathLOG(rat); //((rat-1)/-2.3);
      //test=Math.LN10(rat);
    }
  //replace the artifactual data at positon 120 with the prior value
  //the point has no real valye, so this is just to better visualize the results

P700_DIRK_averaged_trace[120]=P700_DIRK_averaged_trace[119];
output.P700_DIRK_averaged_trace=P700_DIRK_averaged_trace.slice(P700_begining_of_subtrace_for_linear_fit, P700_end_of_subtrace_for_linear_fit);

var begin_trace_index=100;
var end_trace_index=120;
var number_of_points_to_fit=end_trace_index-begin_trace_index;
var P700expData=P700_DIRK_averaged_trace.slice(begin_trace_index,end_trace_index);

var P700_time_per_point=1.5; //entger the delta time between points (only work with constant delta time)

// Obtain best fit results for ECS decay using non-linear least squares fitting
var P700tdata=[];
for (i in expData) {
	P700tdata.push([i*time_per_point, P700expData[i]]);
}

var a = 1;
var b = 1;
var c = 1;

try{
	var fit = NonLinearRegression(P700tdata,{
	   equation: 'b + a * e(- x / c)',
	   initial: [ a, b, c ]
	});

	a= fit.parameters[0].value;
	b= fit.parameters[1].value;
	c = fit.parameters[2].value;

	var P700_outdata=[];
	for (i in expData) {
		P700_outdata.push(b + a*Math.exp(-1*i/c));
	}

	output.P700_fitinput=P700_outdata;
	output.P700_outdata=outdata;

	// Available for all parameters
	output.P700_DIRK_ampl = MathROUND(a, 5);

	output.tP700 = MathROUND(0.001*c, 4);
	output.kP700 = MathROUND(1000/c, 4);
	var v_initial_P700 = output.P700_DIRK_ampl *  output.kP700;
	output.v_initial_P700 = MathROUND(v_initial_P700, 7);
}
catch(e){}

// Display the DIRKf results and calculate LEFd

  output.LEFd_trace = LEFd_trace;

  // Display of PAM result and calculation of the fluorescence parameters
//************************************************************************************************
  //Calculate the PAM fluorescence paramters

  output.data_raw_PAM = PAM.data_raw.slice(0,310);
  data=PAM.data_raw.slice(0,310);

// Set our Apparent FmPrime, 3 FmPrime steps, and Fs to calculate both traditional fv/fm and new Multi-phase flash fv/fm
//----------------------------

//get the values for representative Fs
var baseline=0; //for the time being, do not use baseline

//GET THE VALUES FOR Fs
var Fs = MathMEAN(data.slice(Fs_begin,Fs_end)) - baseline; // take only the first 4 values in the Fs range, excluding the very first
var Fs_std = MathSTDEV(data.slice(Fs_begin,Fs_end)); // create standard deviation for this value for error checking
output.Fs=MathROUND(Fs,2);

//GET THE VALUES FOR THE 5 Fm' ILLUMINATION CONDITIONS

var sat_vals = data.slice(Fm_1_begin,Fm_1_end).sort();  // sort the saturating light values from low to high
var AFmP = MathMEAN(sat_vals.slice(2,20)) - baseline; // take the 18 largest values and average them
var AFmP_std = MathSTDEV(sat_vals); // create standard deviation for this value for error checking
//output.AFmP=AFmP;

sat_vals = data.slice(Fm_5_begin,Fm_5_end).sort();  // sort the saturating light values from low to high
var FmP_end = MathMEAN(sat_vals.slice(2,23)) - baseline; // take the 21 largest values and average them
var FmP_end_std = MathSTDEV(sat_vals); // create standard deviation for this value for error checking
//output.FmP_end=FmP_end;

sat_vals = data.slice(Fm_2_begin,Fm_2_end).sort();  // sort the saturating light values from low to high
var FmP_step1 = MathMEAN(sat_vals.slice(2,6)) - baseline; // take the 4 largest values and average them
var FmP_step1_std = MathSTDEV(sat_vals); // create standard deviation for this value for error checking
//output.FmP_step1=FmP_step1;

sat_vals = data.slice(Fm_3_begin,Fm_3_end).sort();  // sort the saturating light values from low to high
var FmP_step2 = MathMEAN(sat_vals.slice(2,6)) - baseline; // take the 4 largest values and average them
var FmP_step2_std = MathSTDEV(sat_vals); // create standard deviation for this value for error checking
//output.FmP_step2=FmP_step2;

sat_vals = data.slice(Fm_4_begin,Fm_4_end).sort();  // sort the saturating light values from low to high
var FmP_step3 = MathMEAN(sat_vals.slice(2,6)) - baseline; // take the 4 largest values and average them
var FmP_step3_std = MathSTDEV(sat_vals); // create standard deviation for this value for error checking
//output.FmP_ste32=FmP_step3;

// Calculations for F0'
// ----------------------------
var FoPrime_values =   data.slice(FoPrime_begin,FoPrime_end).sort();

//var FoPrime = MathMEAN(FoPrime_values.slice(5,10)) - baseline;
var FoPrime = MathMIN(FoPrime_values);
var FoPrime_std = MathSTDEV(FoPrime_values); // create standard deviation for this value for error checking
output.FoPrime=MathROUND(FoPrime,2);

//output.FoPrime_values=FoPrime_values;

// Calculations for corrected FmPrime using multi-phase flash
// ----------------------------
var reg = MathLINREG(inverse_intensity, [AFmP,FmP_step1,FmP_step2,FmP_step3]);

// Calculate Phi2 w/ and w/out multi-phase flash
// ----------------------------
var fvfm_noMPF = (AFmP-Fs)/AFmP;
var fvfm_MPF = (reg.b-Fs)/reg.b;


// Calculate NPQt, PhiNPQ, PhiNO, qL w/ and w/out multi-phase flash
// ----------------------------
var npqt_MPF = (4.88 / ((reg.b / FoPrime) -1) )-1;
var npqt_noMPF = (4.88 / ((AFmP / FoPrime) -1) )-1;
var qL_MPF = ((reg.b - Fs)*FoPrime)/((reg.b-FoPrime)*Fs);
var qL_noMPF = ((AFmP - Fs)*FoPrime)/((AFmP-FoPrime)*Fs);
var PhiNO_MPF = 1/(npqt_MPF + 1 + qL_MPF*4.88); //based on equation 52 in Kramer et al., 2004 PRES
var PhiNO_noMPF = 1/(npqt_noMPF + 1 + qL_noMPF*4.88); //based on equation 52 in Kramer et al., 2004 PRES
var PhiNPQ_MPF = 1-fvfm_MPF-PhiNO_MPF; //based on equation 53 in Kramer et al., 2004 PRES
var PhiNPQ_noMPF = 1-fvfm_noMPF-PhiNO_noMPF; //based on equation 53 in Kramer et al., 2004 PRES
var qP_MPF = (reg.b - Fs)/(reg.b - FoPrime);
var qP_noMPF = (FmPrime - Fs)/(FmPrime - FoPrime);
var FvP_FmP_MPF = (reg.b-FoPrime)/reg.b;
var FvP_FmP_noMPF = (AFmP-FoPrime)/AFmP;

// Create the variables to be printed (assume to use the MPF values unless there is a good reason not to)
// ----------------------------
var fvfm = fvfm_MPF;
var npqt = npqt_MPF;
var PhiNO = PhiNO_MPF;
var PhiNPQ = PhiNPQ_MPF;
var qL = qL_MPF;
var FmPrime = reg.b;
var qP = qP_MPF;
var FvP_FmP = FvP_FmP_MPF;
/****************OUTPUT VALUES FROM MACRO *******************/

// if any of the flag conditions are true, then create the 'flag' object.  Otherwise, do not create the flag object.
// for now since flag system isn't fully implemented, also create as separate objects so they will be displayed
// ----------------------------

// If multi-phase flash steps are flat or positive slope, then just use the normal Phi2, NPQt, PhiNPQ, PhiNO... etc.
// If Phi2 or NPQt is less than zero, make zero and give user warning.  If Phi2 is higher than .85, give user danger flag.
// ----------------------------




if (reg.m > 0) {
  fvfm = fvfm_noMPF;
  npqt = npqt_noMPF;
  PhiNO = PhiNO_noMPF;
  PhiNPQ = PhiNPQ_noMPF;
  qL = qL_noMPF;
  FmPrime = AFmP;
  qP = qP_noMPF;
  FvP_FmP = FvP_FmP_noMPF;

  if (fvfm <= -.10) {
	danger("Phi2 is outside of expected range, please consider discarding the measurement",output);
  }
  if (fvfm >=.85) {
	danger("Phi2 is outside of expected range, please consider discarding the measurement", output);
  }
  else {
	  output["Phi2"] 		= MathROUND(fvfm,3);
  }


  if (PhiNPQ <= -.10) {
	danger("PhiNPQ is outside of expected range, please consider discarding the measurement",output);
  }
   if (PhiNPQ >= 1.1) {
	danger("PhiNPQ is outside of expected range, please consider discarding the measurement",output);
  }
  else {
	  output.PhiNPQ= MathROUND(PhiNPQ,3);
  }
	  output.qL= MathROUND(qL,3);
	  output.NPQt= MathROUND(npqt,3);
	  output.PhiNO= MathROUND(PhiNO,3);
	  output.FvP_over_FmP = MathROUND(FvP_FmP,3);
	  outputqP = MathROUND(qP,3);

    if (PhiNO <= -.10) {
	danger("PhiNO is outside of expected range, please consider discarding the measurement",output);
  }
   if (PhiNO >= 1.1) {
	danger("PhiNO is outside of expected range, please consider discarding the measurement",output);
  }
  else {
	  output.PhiNO= MathROUND(PhiNO,3);
  }
	  output.qL= MathROUND(qL,3);
	  output.NPQt= MathROUND(npqt,3);
	  output.PhiNPQ= MathROUND(PhiNPQ,3);
	  output.FvP_over_FmP = MathROUND(FvP_FmP,3);
	  outputqP = MathROUND(qP,3);
}

// Otherwise, use the multi-phase flash calculation for Phi2, NPQt, PhiNPQ, PhiNO... etc.
// If Phi2 or NPQt is less than zero, make zero and give user warning.  If Phi2 is higher than .85, give user danger flag.
// ----------------------------
else {

  if (fvfm <= -.10) {
	danger("Phi2 is outside of expected range, please consider discarding the measurement",output);
  }
  if (fvfm >=.85) {
	danger("Phi2 is outside of expected range, please consider discarding the measurement", output);
  }
  else {
	  output["Phi2"] 		= MathROUND(fvfm,3);
  }

  if (PhiNPQ <= -.10) {
	danger("PhiNPQ is outside of expected range, please consider discarding the measurement",output);
  }
   if (PhiNPQ >= 1.1) {
	danger("PhiNPQ is outside of expected range, please consider discarding the measurement",output);
  }
  else {
	  output.PhiNPQ= MathROUND(PhiNPQ,3);
  }
	  output.qL= MathROUND(qL,3);
	  output.NPQt= MathROUND(npqt,3);
	  output.PhiNO= MathROUND(PhiNO,3);
	  output.FvP_over_FmP = MathROUND(FvP_FmP,3);
	  outputqP = MathROUND(qP,3);

    if (PhiNO <= -.10) {
	danger("PhiNO is outside of expected range, please consider discarding the measurement",output);
  }
   if (PhiNO >= 1) {
	danger("PhiNO is outside of expected range, please consider discarding the measurement",output);
  }
  else {
	  output.PhiNO= MathROUND(PhiNO,3);
  }
	  output.qL= MathROUND(qL,3);
	  output.NPQt= MathROUND(npqt,3);
	  output.PhiNPQ= MathROUND(PhiNPQ,3);
	  output.FvP_over_FmP = MathROUND(FvP_FmP,3);
	  outputqP = MathROUND(qP,3);
}

output.FmPrime=MathROUND(FmPrime,2);



// var Phi2_bar ="0: ";
// for (var i=0; i< (10*output.Phi2); i++){
//   Phi2_bar+="█";
// }
// for (var i=10*output.Phi2; i< 10; i++){
//   Phi2_bar+="░";
// }
// Phi2_bar+=" :1.0 (";
// Phi2_bar+=output.Phi2;
// Phi2_bar+=")";

// output.YII=Phi2_bar;

// only display LEF if there is a light intensity measurement > 0
// ----------------------------
if (typeof json.light_intensity != "undefined" && json.light_intensity > 0) {
	output.LEF= MathROUND((fvfm  * 0.45 * json.light_intensity),3);
}

// Calculate Standard Deviation for Warning or Danger flags (out of bounds measurement)
// ----------------------------

if (Fs_std > 100) {
	danger("noisy Fs", output);
}
/*
if (AFmP_std > 300) {
	danger("noisy FmPrime", output);
}
*/
/*
if (FmP_step1_std > 120 | FmP_step2_std > 120 | FmP_step3_std > 120 | FmP_end_std > 300) {
	danger("noisy  multi-phase flash steps",output);
}
*/
if (FoPrime_std > 150) {
	danger("noisy FoPrime", output);
}

 //ANALYZE THE PHI-PSI DATA

var ctrace=PAM.data_raw;
var PSI_trace_beg=310;
var PSI_trace_end=615;
var PSI_trace_length=PSI_trace_end-PSI_trace_beg;
var PSI_data=PAM.data_raw.slice(PSI_trace_beg,PSI_trace_end);
var PSI_dark_raw=MathMEAN(PSI_data.slice(PSI_dark_beg,PSI_dark_end));
var PSI_data_absorbance=[];

for (var i=0; i<PSI_trace_length; i++){
  	  PSI_data_absorbance.push(MathLOG(PSI_dark_raw/PSI_data[i]));
  }
  var PSI_dark=MathMEAN(PSI_data_absorbance.slice(PSI_dark_beg,PSI_dark_end));
  var PSI_ss=MathMEAN(PSI_data_absorbance.slice(PSI_ss_beg,PSI_ss_end));
  var PSI_sat1=MathMEAN(PSI_data_absorbance.slice(PSI_sat1_beg,PSI_sat1_end));
  var PSI_sat2=MathMEAN(PSI_data_absorbance.slice(PSI_sat2_beg,PSI_sat2_end));
  var PSI_ss=1000*MathMEAN(PSI_data_absorbance.slice(PSI_ss_beg,PSI_ss_end));
  var PSI_sat1_vals = PSI_data_absorbance.slice(PSI_sat1_beg,PSI_sat1_end).sort();  // sort the saturating light values from low to high
  var length_of_sat1=PSI_sat1_end-PSI_sat1_beg;
  var top_20_percent=(length_of_sat1*0.8);
  var PSI_sat1 = 1000*MathMEAN(PSI_sat1_vals.slice(top_20_percent,length_of_sat1)); // take the top 20% largest values and average them
  var PSI_sat2_vals = PSI_data_absorbance.slice(PSI_sat2_beg,PSI_sat2_end).sort();  // sort the saturating light values from low to high
  var length_of_sat2=PSI_sat2_end-PSI_sat2_beg;
  var top_20_percent=(length_of_sat2*0.8);
  var PSI_sat2 = 1000*MathMEAN(PSI_sat2_vals.slice(top_20_percent,length_of_sat2)); // take the top 20% largest values and average them
  var PSI_ox=PSI_ss/PSI_sat2;
  var PSI_act=PSI_sat2;
  var PSI_open=(PSI_sat1-PSI_ss)/PSI_sat2;
  var PSI_or=1-PSI_sat1/PSI_sat2;
  output.PSI_data_absorbance=PSI_data_absorbance;

  output["PS1 Active Centers"]=MathROUND(PSI_act, 3);
  output["PS1 Open Centers"]=MathROUND(PSI_open, 3);
  output["PS1 Over Reduced Centers"]=MathROUND(PSI_or, 3);
  output["PS1 Oxidized Centers"]=MathROUND(PSI_ox, 3);
    //output.PSI_dark=PSI_dark;
  //output.PSI_ss = PSI_ss; //MathROUND(PSI_ss, 3);
  //output.PSI_sat1 =MathROUND(PSI_sat1, 3);
  //output.PSI_sat2 =MathROUND(PSI_sat2, 3);

	//output.data_raw_PSI =PSI_data;
   //output.PSI_dark_beg=PSI_dark_beg;
   //output.PSI_dark_end=PSI_dark_end;

// Humidity changes
    var humidity_kinetics=[
                        DIRK_ECS.humidity,
                        P700_DIRK.humidity,
                        PAM.humidity
                       ];
  	output.humidity_K=humidity_kinetics;
  // 	output.humidity_K_T=humidity_kinetics.join(", ");

    var humidity2_kinetics=[
                        DIRK_ECS.humidity2,
                        P700_DIRK.humidity2,
                        PAM.humidity2
                       ];

	output.humidity2_K=humidity2_kinetics;
  // 	output.humidity2_K_T=humidity2_kinetics.join(", ");


   // changes in leaf contactless_temp

    var air_temp_kinetics=[
                                DIRK_ECS.temperature,
                                P700_DIRK.temperature,
                                PAM.temperature
                       ];

     var contactless_temp_kinetics=[
                                DIRK_ECS.contactless_temp,
                                P700_DIRK.contactless_temp,
                                PAM.contactless_temp
                       ];


  output.air_temp_kinetics=air_temp_kinetics;
  output.leaf_thickness=DIRK_ECS.thickness;
  output.LEAF_temp=contactless_temp_kinetics;
// output.LEAF_temp_T=contactless_temp_kinetics.join(", ");

//var air_flow=new Array();

//for (var i=1; i<12 ; i++){
//  	air_flow.push(json.set[i].air_flow);
//}

//output.air_flow=air_flow;

 var light_intensity=DIRK_ECS.light_intensity;
  output["Light Intensity (PAR)"]= light_intensity;

  var ambient_temperature=json.set[3].temperature;
  output["Ambient Temperature"]=ambient_temperature;

  var chamber_temperature=json.set[3].temperature2;
  output["Chamber Temperature"]=chamber_temperature;

  var ambient_humidity=json.set[3].humidity;
  output["Ambient Humidity"]=ambient_humidity;
  var ambient_pressure=json.set[3].pressure;
  output["Ambient Pressure"]=ambient_pressure;
  //var leaf_RH=json.set[1].humidity2;
  //output.leaf_RH=MathROUND(leaf_RH, 2);
  leaf_temperature = DIRK_ECS.contactless_temp;
  output["Leaf Temperature"]=leaf_temperature;
  var leaf_air_difference_temperature = leaf_temperature-ambient_temperature;
  output["Leaf Temperature Differential"]=MathROUND(leaf_air_difference_temperature,3);
  output.LEF=MathROUND(0.45*output.Phi2*output["Light Intensity (PAR)"],2);

  var leaf_temperature_differential = ambient_temperature;
  output["Leaf Temperature Differenial"]=MathROUND(leaf_temperature- leaf_temperature_differential, 2);

var newSpad = GetProtocolByLabel("SPAD", json);
output.SPAD = newSpad.spad[0];



return output;

