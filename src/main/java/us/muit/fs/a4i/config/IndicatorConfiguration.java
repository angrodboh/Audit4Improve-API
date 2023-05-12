/**
 * 
 */
package us.muit.fs.a4i.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.logging.log4j.Level;

import us.muit.fs.a4i.model.entities.IndicatorI.IndicatorState;
import us.muit.fs.a4i.model.entities.ReportItemI;

/**
 * @author Isabel Román
 *
 */
public class IndicatorConfiguration implements IndicatorConfigurationI {

	private static Logger log = Logger.getLogger(Checker.class.getName());

	@Override
	/**
	 * <p>
	 * Comprueba si el indicador está definido en el fichero por defecto o en el de
	 * la aplicación cliente
	 * </p>
	 * <p>
	 * También verifica que el tipo es el adecuado
	 * </p>
	 * 
	 * @param indicatorName nombre del indicador que se quiere comprobar
	 * @param indicatorType tipo del indicador
	 * @return indicatorDefinition Si el indicador está definido y el tipo es
	 *         correcto se devuelve un mapa con las unidades y la descripción
	 * @throws FileNotFoundException Si no se localiza el fichero de configuración
	 */
	public HashMap<String, String> definedIndicator(String name, String type) throws FileNotFoundException {
		HashMap<String, String> indicatorDefinition = null;
		log.info("Checker solicitud de búsqueda indicador " + name);

		String filePath = "/" + Context.getDefaultRI();
		log.info("Buscando el archivo " + filePath);
		InputStream is = this.getClass().getResourceAsStream(filePath);
		log.info("InputStream " + is + " para " + filePath);
		InputStreamReader isr = new InputStreamReader(is);

		indicatorDefinition = isDefinedIndicator(name, type, isr);
		if ((indicatorDefinition == null) && Context.getAppRI() != null) {
			is = new FileInputStream(Context.getAppRI());
			isr = new InputStreamReader(is);
			indicatorDefinition = isDefinedIndicator(name, type, isr);
		}

		return indicatorDefinition;
	}

	private HashMap<String, String> isDefinedIndicator(String indicatorName, String indicatorType,
			InputStreamReader isr) throws FileNotFoundException {
		HashMap<String, String> indicatorDefinition = null;

		JsonReader reader = Json.createReader(isr);
		log.info("Creo el JsonReader");

		JsonObject confObject = reader.readObject();
		log.info("Leo el objeto");
		reader.close();

		log.info("Muestro la configuración leída " + confObject);
		JsonArray indicators = confObject.getJsonArray("indicators");
		log.info("El número de indicadores es " + indicators.size());
		for (int i = 0; i < indicators.size(); i++) {
			log.info("nombre: " + indicators.get(i).asJsonObject().getString("name"));
			if (indicators.get(i).asJsonObject().getString("name").equals(indicatorName)) {
				log.info("Localizado el indicador");
				log.info("tipo: " + indicators.get(i).asJsonObject().getString("type"));
				if (indicators.get(i).asJsonObject().getString("type").equals(indicatorType)) {
					indicatorDefinition = new HashMap<String, String>();
					indicatorDefinition.put("description", indicators.get(i).asJsonObject().getString("description"));
					indicatorDefinition.put("unit", indicators.get(i).asJsonObject().getString("unit"));
					
					JsonObject limits = indicators.get(i).asJsonObject().getJsonObject("limits");
					int okLimit = 0;
					int warningLimit = 0;
					int criticalLimit = 0;
					
					if(limits != null) {
						okLimit = limits.getInt("ok");
						warningLimit = limits.getInt("warning");
						criticalLimit = limits.getInt("critical");
					} else {
						log.info("El fichero de configuración no tiene límites, se van a utilizar los valores por defecto.");
					}
					
					indicatorDefinition.put("limits.ok", Integer.toString(okLimit));
					indicatorDefinition.put("limits.warning", Integer.toString(warningLimit));
					indicatorDefinition.put("limits.critical", Integer.toString(criticalLimit));
				}

			}
		}

		return indicatorDefinition;
	}

	@Override
	public List<String> listAllIndicators() throws FileNotFoundException {
		log.info("Consulta todas las métricas");

		List<String> allmetrics = new ArrayList<String>();

		String filePath = "/" + Context.getDefaultRI();
		log.info("Buscando el archivo " + filePath);
		InputStream is = this.getClass().getResourceAsStream(filePath);
		log.info("InputStream " + is + " para " + filePath);
		InputStreamReader isr = new InputStreamReader(is);

		JsonReader reader = Json.createReader(isr);
		log.info("Creo el JsonReader");

		JsonObject confObject = reader.readObject();
		log.info("Leo el objeto");
		reader.close();

		log.info("Muestro la configuración leída " + confObject);
		JsonArray metrics = confObject.getJsonArray("indicators");
		log.info("El número de indicadores es " + metrics.size());
		for (int i = 0; i < metrics.size(); i++) {
			log.info("Añado nombre: " + metrics.get(i).asJsonObject().getString("name"));
			allmetrics.add(metrics.get(i).asJsonObject().getString("name"));
		}
		if (Context.getAppRI() != null) {
			is = new FileInputStream(Context.getAppRI());
			isr = new InputStreamReader(is);
			reader = Json.createReader(isr);
			confObject = reader.readObject();
			reader.close();

			log.info("Muestro la configuración leída " + confObject);
			metrics = confObject.getJsonArray("metrics");
			log.info("El número de métricas es " + metrics.size());
			for (int i = 0; i < metrics.size(); i++) {
				log.info("Añado nombre: " + metrics.get(i).asJsonObject().getString("name"));
				allmetrics.add(metrics.get(i).asJsonObject().getString("name"));
			}
		}

		return allmetrics;
	}

	@Override
	public IndicatorState getIndicatorState(ReportItemI indicator) throws FileNotFoundException {
		//TODO: change indicator definitions key name to a constant.
		String className = indicator.getValue().getClass().getName();
		HashMap<String, String> indicatorDefinition = definedIndicator(indicator.getName(), className);
		
		IndicatorState finalState = IndicatorState.UNDEFINED;
		String criticalLimit = indicatorDefinition.get("limits.critical");
		String warningLimit = indicatorDefinition.get("limits.warning");
		String okLimit = indicatorDefinition.get("limits.ok");
		
		
		// Se tienen en cuenta los posibles tipos de indicadores para compararlos
		if(criticalLimit != null && warningLimit != null && okLimit != null) {
			if(className == Integer.class.getName()) {
				Integer value = (Integer) indicator.getValue();
				
				if(value >= Integer.parseInt(criticalLimit)) finalState = IndicatorState.CRITICAL;
				else if(value >= Integer.parseInt(warningLimit)) finalState = IndicatorState.WARNING;
				else if(value > Integer.parseInt(okLimit)) finalState = IndicatorState.OK;
				
			} else if(className == Double.class.getName()) {
				Double value = (Double) indicator.getValue();
				
				if(value >= Integer.parseInt(criticalLimit)) finalState = IndicatorState.CRITICAL;
				else if(value >= Integer.parseInt(warningLimit)) finalState = IndicatorState.WARNING;
				else if(value > Integer.parseInt(okLimit)) finalState = IndicatorState.OK;
				
			}
		} else {
			log.warning("No se han encontrado límites definidos para el indicador: " + indicator.getName());
		}
		
		
		return finalState;
	}

}
