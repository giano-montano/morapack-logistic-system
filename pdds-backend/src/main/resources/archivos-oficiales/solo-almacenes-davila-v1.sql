-- MySQL dump 10.13  Distrib 8.0.38, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: bdpddsdev
-- ------------------------------------------------------
-- Server version	8.0.39

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `almacen`
--

LOCK TABLES `almacen` WRITE;
/*!40000 ALTER TABLE `almacen` DISABLE KEYS */;
INSERT INTO `almacen` (`id`, `activo`, `capacidad_maxima`, `capacidad_ocupada`, `codigo_aeropuerto_en4letras`, `codigo_ciudad_en4letras`, `continente`, `es_infinito`, `gmt`, `latitud`, `longitud`, `nombre_ciudad`, `nombre_pais`) VALUES (1,_binary '',430,0,'SKBO','bogo','SUDAMERICA',_binary '\0',-5,4.701388888888889,-74.14694444444446,'Bogota','Colombia'),(2,_binary '',410,0,'SEQM','quit','SUDAMERICA',_binary '\0',-5,0.11333333333333334,-78.3586111111111,'Quito','Ecuador'),(3,_binary '',400,0,'SVMI','cara','SUDAMERICA',_binary '\0',-4,10.603055555555555,-66.99055555555556,'Caracas','Venezuela'),(4,_binary '',480,0,'SBBR','bras','SUDAMERICA',_binary '\0',-3,-15.864722222222222,-47.918055555555554,'Brasilia','Brasil'),(5,_binary '',440,0,'SPIM','lima','SUDAMERICA',_binary '',-5,-12.021944444444445,-77.11444444444444,'Lima','Per�'),(6,_binary '',420,0,'SLLP','lapa','SUDAMERICA',_binary '\0',-4,-16.513055555555557,-68.19222222222223,'La Paz','Bolivia'),(7,_binary '',460,0,'SCEL','sant','SUDAMERICA',_binary '\0',-3,-33.396388888888886,-70.79472222222222,'Santiago de Chile','Chile'),(8,_binary '',460,0,'SABE','buen','SUDAMERICA',_binary '\0',-3,-34.55916666666666,-58.41555555555556,'Buenos Aires','Argentina'),(9,_binary '',400,0,'SGAS','asun','SUDAMERICA',_binary '\0',-4,-25.240000000000002,-57.519999999999996,'Asunci�n','Paraguay'),(10,_binary '',400,0,'SUAA','mont','SUDAMERICA',_binary '\0',-3,-34.78916666666667,-56.264722222222225,'Motenvideo','Uruguay'),(11,_binary '',410,0,'LATI','tira','EUROPA',_binary '\0',2,41.414722222222224,19.720555555555553,'Tirana','Albania'),(12,_binary '',480,0,'EDDI','berl','EUROPA',_binary '\0',2,52.47361111111111,13.401666666666667,'Berlin','Alemania'),(13,_binary '',430,0,'LOWW','vien','EUROPA',_binary '\0',2,48.11083333333333,16.570833333333333,'Viena','Austria'),(14,_binary '',440,0,'EBCI','brus','EUROPA',_binary '',2,50.45916666666667,4.453611111111111,'Bruselas','Belgica'),(15,_binary '',400,0,'UMMS','mins','EUROPA',_binary '\0',3,53.8825,28.0325,'Minsk','Bielorrusia'),(16,_binary '',400,0,'LBSF','sofi','EUROPA',_binary '\0',3,42.69027777777777,23.404722222222222,'Sofia','Bulgaria'),(17,_binary '',400,0,'LKPR','prag','EUROPA',_binary '\0',2,50.10138888888889,14.265555555555556,'Praga','Checa'),(18,_binary '',420,0,'LDZA','zagr','EUROPA',_binary '\0',2,45.74277777777778,16.06861111111111,'Zagreb','Croacia'),(19,_binary '',480,0,'EKCH','cope','EUROPA',_binary '\0',2,55.61805555555556,12.65611111111111,'Copenhague','Dinamarca'),(20,_binary '',480,0,'EHAM','amst','EUROPA',_binary '\0',2,52.3,4.765,'Amsterdam','Holanda'),(21,_binary '',480,0,'VIDP','delh','ASIA',_binary '\0',5,28.56638888888889,77.10305555555556,'Delhi','India'),(22,_binary '',400,0,'OSDI','dama','ASIA',_binary '\0',3,33.41138888888889,36.51555555555556,'Damasco','Siria'),(23,_binary '',420,0,'OERK','riad','ASIA',_binary '\0',3,24.95777777777778,46.69888888888889,'Riad','Arabia Saudita'),(24,_binary '',420,0,'OMDB','emir','ASIA',_binary '\0',4,25.252777777777776,55.364444444444445,'Dubai','Emiratos A.U'),(25,_binary '',480,0,'OAKB','kabu','ASIA',_binary '\0',4,34.565555555555555,69.21083333333334,'Kabul','Afganistan'),(26,_binary '',460,0,'OOMS','masc','ASIA',_binary '\0',4,23.589444444444442,58.284166666666664,'Mascate','Oman'),(27,_binary '',420,0,'OYSN','sana','ASIA',_binary '\0',3,15.476111111111111,44.219722222222224,'Sana','Yemen'),(28,_binary '',410,0,'OPKC','kara','ASIA',_binary '\0',5,24.9,67.15,'Karachi','Pakistan'),(29,_binary '',400,0,'UBBB','baku','ASIA',_binary '',2,40.467222222222226,50.04666666666667,'Baku','Azerbaiyan'),(30,_binary '',400,0,'OJAI','aman','ASIA',_binary '\0',3,31.722499999999997,35.99333333333333,'Aman','Jordania');
/*!40000 ALTER TABLE `almacen` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-11-13  0:29:10
