/***********************************************************************
* Copyright by Michael Loesler, https://software.applied-geodesy.org   *
*                                                                      *
* This program is free software; you can redistribute it and/or modify *
* it under the terms of the GNU General Public License as published by *
* the Free Software Foundation; either version 3 of the License, or    *
* at your option any later version.                                    *
*                                                                      *
* This program is distributed in the hope that it will be useful,      *
* but WITHOUT ANY WARRANTY; without even the implied warranty of       *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the        *
* GNU General Public License for more details.                         *
*                                                                      *
* You should have received a copy of the GNU General Public License    *
* along with this program; if not, see <http://www.gnu.org/licenses/>  *
* or write to the                                                      *
* Free Software Foundation, Inc.,                                      *
* 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.            *
*                                                                      *
***********************************************************************/

package org.applied_geodesy.adjustment.geometry.surface;

import java.util.Collection;
import java.util.List;

import org.applied_geodesy.adjustment.geometry.Quaternion;
import org.applied_geodesy.adjustment.geometry.SurfaceFeature;
import org.applied_geodesy.adjustment.geometry.parameter.ParameterType;
import org.applied_geodesy.adjustment.geometry.parameter.ProcessingType;
import org.applied_geodesy.adjustment.geometry.parameter.UnknownParameter;
import org.applied_geodesy.adjustment.geometry.point.FeaturePoint;
import org.applied_geodesy.adjustment.geometry.restriction.AverageRestriction;
import org.applied_geodesy.adjustment.geometry.restriction.ProductSumRestriction;
import org.applied_geodesy.adjustment.geometry.restriction.TrigonometricRestriction;
import org.applied_geodesy.adjustment.geometry.restriction.TrigonometricRestriction.TrigonometricFunctionType;
import org.applied_geodesy.adjustment.geometry.surface.primitive.Cone;
import org.applied_geodesy.adjustment.geometry.surface.primitive.Plane;
import org.applied_geodesy.adjustment.geometry.surface.primitive.Sphere;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.MatrixSingularException;
import no.uib.cipr.matrix.NotConvergedException;
import no.uib.cipr.matrix.SVD;

public class CircularConeFeature extends SurfaceFeature {
	
	private final Cone cone;

	public CircularConeFeature() {
		super(true);
		
		this.cone = new Cone();
		
		UnknownParameter A = this.cone.getUnknownParameter(ParameterType.MAJOR_AXIS_COEFFICIENT);
		UnknownParameter C = this.cone.getUnknownParameter(ParameterType.MINOR_AXIS_COEFFICIENT);
		
		UnknownParameter R21 = this.cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R21);
		
		A.setVisible(false);
		C.setVisible(false);
		R21.setProcessingType(ProcessingType.FIXED);

		UnknownParameter invB    = new UnknownParameter(ParameterType.MIDDLE_AXIS_COEFFICIENT, false, 0.0, false, ProcessingType.POSTPROCESSING);
		UnknownParameter oneHalf = new UnknownParameter(ParameterType.CONSTANT,                false, 0.5, false, ProcessingType.FIXED);
		UnknownParameter phi     = new UnknownParameter(ParameterType.ANGLE,                   false, 0.0, true,  ProcessingType.POSTPROCESSING);

		AverageRestriction AequalsCRestriction            = new AverageRestriction(true, List.of(A), C);
		ProductSumRestriction invertedBRestriction        = new ProductSumRestriction(false, List.of(A, C), List.of(oneHalf, oneHalf), -1.0, Boolean.TRUE, invB);
		TrigonometricRestriction trigonometricRestriction = new TrigonometricRestriction(false, TrigonometricFunctionType.TANGENT, Boolean.TRUE, invB, phi);
		
		this.add(this.cone);
		
		this.getUnknownParameters().addAll(phi, invB, oneHalf);
		this.getRestrictions().add(AequalsCRestriction);
		this.getPostProcessingCalculations().addAll(
				invertedBRestriction,
				trigonometricRestriction
		);
	}
	
	public Cone getCone() {
		return this.cone;
	}

	public static void deriveInitialGuess(Collection<FeaturePoint> points, CircularConeFeature feature) throws IllegalArgumentException, NotConvergedException, UnsupportedOperationException {
		deriveInitialGuess(points, feature.cone);
	}
	
	public static void deriveInitialGuess(Collection<FeaturePoint> points, Cone cone) throws IllegalArgumentException, NotConvergedException, UnsupportedOperationException {
		int nop = 0;
		
		for (FeaturePoint point : points) {
			if (!point.isEnable())
				continue;
			
			nop++;
			if (cone.getDimension() > point.getDimension())
				throw new IllegalArgumentException("Error, could not estimate center of mass because dimension of points is inconsistent, " + cone.getDimension() + " != " + point.getDimension());
		}
		
		if (nop < 6)
			throw new IllegalArgumentException("Error, the number of points is not sufficient; at least 6 points are needed.");

		
		// derive initial guess
		if (nop > 8) {
			ConeFeature.deriveInitialGuess(points, cone);
			UnknownParameter A = cone.getUnknownParameter(ParameterType.MAJOR_AXIS_COEFFICIENT);
			UnknownParameter C = cone.getUnknownParameter(ParameterType.MINOR_AXIS_COEFFICIENT);
			
			UnknownParameter R11 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R11);
			UnknownParameter R12 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R12);
			UnknownParameter R13 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R13);
			
			UnknownParameter R21 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R21);
			UnknownParameter R22 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R22);
			UnknownParameter R23 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R23);
			
			UnknownParameter R31 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R31);
			UnknownParameter R32 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R32);
			UnknownParameter R33 = cone.getUnknownParameter(ParameterType.ROTATION_COMPONENT_R33);
			
			double b = 0.5 * (A.getValue0() + C.getValue0());
			
			double rx = Math.atan2( R32.getValue0(), R33.getValue0());
			double ry = Math.atan2(-R31.getValue0(), Math.hypot(R32.getValue0(), R33.getValue0()));
						
			// derive rotation sequence without rz, i.e., rz = 0 --> R = Ry*Rx
			double r11 = Math.cos(ry);
			double r12 = Math.sin(rx)*Math.sin(ry);
			double r13 = Math.cos(rx)*Math.sin(ry);
			
			double r21 = 0.0;
			double r22 = Math.cos(rx);
			double r23 =-Math.sin(rx);
			
			double r31 =-Math.sin(ry);
			double r32 = Math.sin(rx)*Math.cos(ry);
			double r33 = Math.cos(rx)*Math.cos(ry);
			
			A.setValue0(b);
			C.setValue0(b);
			
			R11.setValue0(r11);
			R12.setValue0(r12);
			R13.setValue0(r13);
			
			R21.setValue0(r21);
			R22.setValue0(r22);
			R23.setValue0(r23);
			
			R31.setValue0(r31);
			R32.setValue0(r32);
			R33.setValue0(r33);
		}
		else 
			deriveInitialGuessByCircle(points,  cone);
	}
	
	@Override
	public void deriveInitialGuess() throws MatrixSingularException, IllegalArgumentException, NotConvergedException, UnsupportedOperationException {
		deriveInitialGuess(this.cone.getFeaturePoints(), this.cone);
	}
	
	private static void deriveInitialGuessByCircle(Collection<FeaturePoint> points, Cone cone) throws IllegalArgumentException, NotConvergedException, UnsupportedOperationException {
		Sphere sphere = new Sphere();
		Plane plane = new Plane();
		SpatialCircleFeature.deriveInitialGuess(points, sphere, plane);
		
		double x0 = sphere.getUnknownParameter(ParameterType.ORIGIN_COORDINATE_X).getValue0();
		double y0 = sphere.getUnknownParameter(ParameterType.ORIGIN_COORDINATE_Y).getValue0();
		double z0 = sphere.getUnknownParameter(ParameterType.ORIGIN_COORDINATE_Z).getValue0();

		double wx = plane.getUnknownParameter(ParameterType.VECTOR_X).getValue0();
		double wy = plane.getUnknownParameter(ParameterType.VECTOR_Y).getValue0();
		double wz = plane.getUnknownParameter(ParameterType.VECTOR_Z).getValue0();
		
		DenseMatrix w = new DenseMatrix(1, 3, new double[] {wx, wy, wz}, true);
		SVD svd = SVD.factorize(w);
		Matrix V = svd.getVt();
		
		double ux = V.get(0, 1);
		double uy = V.get(1, 1);
		double uz = V.get(2, 1);
		
		double vx = V.get(0, 2);
		double vy = V.get(1, 2);
		double vz = V.get(2, 2);
		
		double det = ux*vy*wz + vx*wy*uz + wx*uy*vz - wx*vy*uz - vx*uy*wz - ux*wy*vz;
		if (det < 0) {
			wx = -wx;
			wy = -wy;
			wz = -wz;
		}
	
		double rx = Math.atan2( wy, wz);
		double ry = Math.atan2(-wx, Math.hypot(wy, wz));

		// derive rotation sequence without rz, i.e., rz = 0 --> R = Ry*Rx
		ux = Math.cos(ry);
		uy = Math.sin(rx)*Math.sin(ry);
		uz = Math.cos(rx)*Math.sin(ry);

		vx = 0.0;
		vy = Math.cos(rx);
		vz =-Math.sin(rx);

		wx =-Math.sin(ry);
		wy = Math.sin(rx)*Math.cos(ry);
		wz = Math.cos(rx)*Math.cos(ry);
		
		Quaternion q = FeatureUtil.getQuaternionHz(new double[] {wx, wy, wz});
		Collection<FeaturePoint> rotatedPoints = FeatureUtil.getRotatedFeaturePoints(points, new double[] {0, 0, 0}, q);
		Quaternion rotatedApexQ = q.rotate(new double[] { x0, y0, z0} );
		double rx0 = rotatedApexQ.getQ1();
		double ry0 = rotatedApexQ.getQ2();
		double rz0 = rotatedApexQ.getQ3();
		double r = 0;
		int nop = 0;
		for (FeaturePoint point : rotatedPoints) {
			if (!point.isEnable())
				continue;
			
			nop++;
			
			double rxi = point.getX0();
			double ryi = point.getY0();
			double rzi = point.getZ0();
			
			double dx = rxi - rx0;
			double dy = ryi - ry0;
			double dz = rzi - rz0;
			
			r += Math.abs(dz*dz / (dx*dx + dy*dy));
		}
		r = Math.sqrt(r/nop);
		cone.setInitialGuess(x0, y0, z0, r, r, ux, uy, uz, vx, vy, vz, wx, wy, wz);
	}
}