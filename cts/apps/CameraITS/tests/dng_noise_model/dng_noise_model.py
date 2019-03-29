# Copyright 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import its.device
import its.caps
import its.objects
import its.image
import os.path
from matplotlib import pylab
import matplotlib
import matplotlib.pyplot as plt
import math
import textwrap
import time
import numpy as np
import scipy.stats
import scipy.signal


# Convert a 2D array a to a 4D array with dimensions [tile_size,
# tile_size, row, col] where row, col are tile indices.
def tile(a, tile_size):
    tile_rows, tile_cols = a.shape[0]/tile_size, a.shape[1]/tile_size
    a = a.reshape([tile_rows, tile_size, tile_cols, tile_size])
    a = a.transpose([1, 3, 0, 2])
    return a


def main():
    """Capture a set of raw images with increasing gains and measure the noise.
    """
    NAME = os.path.basename(__file__).split(".")[0]

    # How many sensitivities per stop to sample.
    steps_per_stop = 2
    # How large of tiles to use to compute mean/variance.
    tile_size = 64
    # Exposure bracketing range in stops
    bracket_stops = 4
    # How high to allow the mean of the tiles to go.
    max_signal_level = 0.5
    # Colors used for plotting the data for each exposure.
    colors = 'rygcbm'

    # Define a first order high pass filter to eliminate low frequency
    # signal content when computing variance.
    f = np.array([-1, 1]).astype('float32')
    # Make it a higher order filter by convolving the first order
    # filter with itself a few times.
    f = np.convolve(f, f)
    f = np.convolve(f, f)

    # Compute the normalization of the filter to preserve noise
    # power. Let a be the normalization factor we're looking for, and
    # Let X and X' be the random variables representing the noise
    # before and after filtering, respectively. First, compute
    # Var[a*X']:
    #
    #   Var[a*X'] = a^2*Var[X*f_0 + X*f_1 + ... + X*f_N-1]
    #             = a^2*(f_0^2*Var[X] + f_1^2*Var[X] + ... + (f_N-1)^2*Var[X])
    #             = sum(f_i^2)*a^2*Var[X]
    #
    # We want Var[a*X'] to be equal to Var[X]:
    #
    #    sum(f_i^2)*a^2*Var[X] = Var[X] -> a = sqrt(1/sum(f_i^2))
    #
    # We can just bake this normalization factor into the high pass
    # filter kernel.
    f /= math.sqrt(np.dot(f, f))

    bracket_factor = math.pow(2, bracket_stops)

    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()

        # Get basic properties we need.
        sens_min, sens_max = props['android.sensor.info.sensitivityRange']
        sens_max_analog = props['android.sensor.maxAnalogSensitivity']
        white_level = props['android.sensor.info.whiteLevel']

        print "Sensitivity range: [%f, %f]" % (sens_min, sens_max)
        print "Max analog sensitivity: %f" % (sens_max_analog)

        # Do AE to get a rough idea of where we are.
        s_ae, e_ae, _, _, _  = \
            cam.do_3a(get_results=True, do_awb=False, do_af=False)
        # Underexpose to get more data for low signal levels.
        auto_e = s_ae*e_ae/bracket_factor

        # If the auto-exposure result is too bright for the highest
        # sensitivity or too dark for the lowest sensitivity, report
        # an error.
        min_exposure_ns, max_exposure_ns = \
            props['android.sensor.info.exposureTimeRange']
        if auto_e < min_exposure_ns*sens_max:
            raise its.error.Error("Scene is too bright to properly expose \
                                  at the highest sensitivity")
        if auto_e*bracket_factor > max_exposure_ns*sens_min:
            raise its.error.Error("Scene is too dark to properly expose \
                                  at the lowest sensitivity")

        # Start the sensitivities at the minimum.
        s = sens_min

        samples = []
        plots = []
        measured_models = []
        while s <= sens_max + 1:
            print "ISO %d" % round(s)
            fig = plt.figure()
            plt_s = fig.gca()
            plt_s.set_title("ISO %d" % round(s))
            plt_s.set_xlabel("Mean signal level")
            plt_s.set_ylabel("Variance")

            samples_s = []
            for b in range(0, bracket_stops + 1):
                # Get the exposure for this sensitivity and exposure time.
                e = int(math.pow(2, b)*auto_e/float(s))
                req = its.objects.manual_capture_request(round(s), e)
                cap = cam.do_capture(req, cam.CAP_RAW)
                planes = its.image.convert_capture_to_planes(cap, props)

                samples_e = []
                for (pidx, p) in enumerate(planes):
                    p = p.squeeze()

                    # Crop the plane to be a multiple of the tile size.
                    p = p[0:p.shape[0] - p.shape[0]%tile_size,
                          0:p.shape[1] - p.shape[1]%tile_size]

                    # convert_capture_to_planes normalizes the range
                    # to [0, 1], but without subtracting the black
                    # level.
                    black_level = its.image.get_black_level(
                        pidx, props, cap["metadata"])
                    p *= white_level
                    p = (p - black_level)/(white_level - black_level)

                    # Use our high pass filter to filter this plane.
                    hp = scipy.signal.sepfir2d(p, f, f).astype('float32')

                    means_tiled = \
                        np.mean(tile(p, tile_size), axis=(0, 1)).flatten()
                    vars_tiled = \
                        np.var(tile(hp, tile_size), axis=(0, 1)).flatten()

                    for (mean, var) in zip(means_tiled, vars_tiled):
                        # Don't include the tile if it has samples that might
                        # be clipped.
                        if mean + 2*math.sqrt(var) < max_signal_level:
                            samples_e.append([mean, var])

                    means_e, vars_e = zip(*samples_e)
                    plt_s.plot(means_e, vars_e, colors[b%len(colors)] + ',')

                    samples_s.extend(samples_e)

            [S, O, R, p, stderr] = scipy.stats.linregress(samples_s)
            measured_models.append([round(s), S, O])
            print "Sensitivity %d: %e*y + %e (R=%f)" % (round(s), S, O, R)

            # Add the samples for this sensitivity to the global samples list.
            samples.extend([(round(s), mean, var) for (mean, var) in samples_s])

            # Add the linear fit to the plot for this sensitivity.
            plt_s.plot([0, max_signal_level], [O, O + S*max_signal_level], 'r-',
                       label="Linear fit")
            xmax = max([x for (x, _) in samples_s])*1.25
            plt_s.set_xlim(xmin=0, xmax=xmax)
            plt_s.set_ylim(ymin=0, ymax=(O + S*xmax)*1.25)
            fig.savefig("%s_samples_iso%04d.png" % (NAME, round(s)))
            plots.append([round(s), fig])

            # Move to the next sensitivity.
            s *= math.pow(2, 1.0/steps_per_stop)

        # Grab the sensitivities and line parameters from each sensitivity.
        S_measured = [e[1] for e in measured_models]
        O_measured = [e[2] for e in measured_models]
        sens = np.asarray([e[0] for e in measured_models])
        sens_sq = np.square(sens)

        # Use a global linear optimization to fit the noise model.
        gains = np.asarray([s[0] for s in samples])
        means = np.asarray([s[1] for s in samples])
        vars_ = np.asarray([s[2] for s in samples])

        # Define digital gain as the gain above the max analog gain
        # per the Camera2 spec. Also, define a corresponding C
        # expression snippet to use in the generated model code.
        digital_gains = np.maximum(gains/sens_max_analog, 1)
        digital_gain_cdef = "(sens / %d.0) < 1.0 ? 1.0 : (sens / %d.0)" % \
            (sens_max_analog, sens_max_analog)

        # Find the noise model parameters via least squares fit.
        ad = gains*means
        bd = means
        cd = gains*gains
        dd = digital_gains*digital_gains
        a = np.asarray([ad, bd, cd, dd]).T
        b = vars_

        # To avoid overfitting to high ISOs (high variances), divide the system
        # by the gains.
        a /= (np.tile(gains, (a.shape[1], 1)).T)
        b /= gains

        [A, B, C, D], _, _, _ = np.linalg.lstsq(a, b)

        # Plot the noise model components with the values predicted by the
        # noise model.
        S_model = A*sens + B
        O_model = \
            C*sens_sq + D*np.square(np.maximum(sens/sens_max_analog, 1))

        (fig, (plt_S, plt_O)) = plt.subplots(2, 1)
        plt_S.set_title("Noise model")
        plt_S.set_ylabel("S")
        plt_S.loglog(sens, S_measured, 'r+', basex=10, basey=10,
                     label="Measured")
        plt_S.loglog(sens, S_model, 'bx', basex=10, basey=10, label="Model")
        plt_S.legend(loc=2)

        plt_O.set_xlabel("ISO")
        plt_O.set_ylabel("O")
        plt_O.loglog(sens, O_measured, 'r+', basex=10, basey=10,
                     label="Measured")
        plt_O.loglog(sens, O_model, 'bx', basex=10, basey=10, label="Model")
        fig.savefig("%s.png" % (NAME))

        for [s, fig] in plots:
            plt_s = fig.gca()

            dg = max(s/sens_max_analog, 1)
            S = A*s + B
            O = C*s*s + D*dg*dg
            plt_s.plot([0, max_signal_level], [O, O + S*max_signal_level], 'b-',
                       label="Model")
            plt_s.legend(loc=2)

            plt.figure(fig.number)

            # Re-save the plot with the global model.
            fig.savefig("%s_samples_iso%04d.png" % (NAME, round(s)))

        # Generate the noise model implementation.
        noise_model_code = textwrap.dedent("""\
            /* Generated test code to dump a table of data for external validation
             * of the noise model parameters.
             */
            #include <stdio.h>
            #include <assert.h>
            double compute_noise_model_entry_S(int sens);
            double compute_noise_model_entry_O(int sens);
            int main(void) {
                int sens;
                for (sens = %d; sens <= %d; sens += 100) {
                    double o = compute_noise_model_entry_O(sens);
                    double s = compute_noise_model_entry_S(sens);
                    printf("%%d,%%lf,%%lf\\n", sens, o, s);
                }
                return 0;
            }

            /* Generated functions to map a given sensitivity to the O and S noise
             * model parameters in the DNG noise model.
             */
            double compute_noise_model_entry_S(int sens) {
                double s = %e * sens + %e;
                return s < 0.0 ? 0.0 : s;
            }

            double compute_noise_model_entry_O(int sens) {
                double digital_gain = %s;
                double o = %e * sens * sens + %e * digital_gain * digital_gain;
                return o < 0.0 ? 0.0 : o;
            }
            """ % (sens_min, sens_max, A, B, digital_gain_cdef, C, D))
        print noise_model_code
        text_file = open("noise_model.c", "w")
        text_file.write("%s" % noise_model_code)
        text_file.close()

if __name__ == '__main__':
    main()

