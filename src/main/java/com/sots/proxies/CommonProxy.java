package com.sots.proxies;

import java.io.File;

import com.sots.util.BlockRegistry;
import com.sots.util.Config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {
	
	public static Configuration config;
	
	public void preInit(FMLPreInitializationEvent event){
		//Read/make the Config file on Startup
		File directory = event.getModConfigurationDirectory();
		config = new Configuration(new File(directory.getPath(), "LogisticsPipes2.cfg"));
		Config.readConfig();
		
		//Load Blocks
		BlockRegistry.init();
	}
	
	public void init(FMLInitializationEvent event){
		
	}
	
	public void postInit(FMLPostInitializationEvent event){
		if(config != null && config.hasChanged()){
			config.save();
		}
	}
}