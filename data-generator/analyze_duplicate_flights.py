#!/usr/bin/env python3
"""
Script to analyze flights.txt and detect flights with the same 
departure storage at the same departure time.
"""

from collections import defaultdict
from pathlib import Path

def parse_flight_line(line):
    """
    Parse a flight line and extract components.
    Format: XXXX-YYYY-HH:MM-HH:MM-NNNN
    Where:
    - XXXX: departure storage code (4 letters)
    - YYYY: arrival storage code (4 letters)
    - First HH:MM: departure time
    - Second HH:MM: arrival time
    - NNNN: capacity (starts from 0)
    """
    parts = line.strip().split('-')
    
    if len(parts) != 5:
        return None
    
    departure_storage = parts[0]
    arrival_storage = parts[1]
    departure_time = parts[2]
    arrival_time = parts[3]
    capacity = parts[4]
    
    return {
        'departure_storage': departure_storage,
        'arrival_storage': arrival_storage,
        'departure_time': departure_time,
        'arrival_time': arrival_time,
        'capacity': capacity,
        'full_line': line.strip()
    }

def analyze_flights(file_path):
    """
    Read flights file and detect duplicates based on 
    departure storage + departure time combination.
    """
    # Dictionary: (departure_storage, departure_time) -> list of flights
    flights_by_departure = defaultdict(list)
    
    total_flights = 0
    invalid_lines = 0
    
    with open(file_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
                
            total_flights += 1
            flight = parse_flight_line(line)
            
            if flight is None:
                invalid_lines += 1
                print(f"⚠️  Invalid line {line_num}: {line}")
                continue
            
            # Group by (departure_storage, departure_time)
            key = (flight['departure_storage'], flight['departure_time'])
            flights_by_departure[key].append({
                'line_num': line_num,
                **flight
            })
    
    # Print summary
    print("=" * 80)
    print("FLIGHT ANALYSIS SUMMARY")
    print("=" * 80)
    print(f"Total flights analyzed: {total_flights}")
    print(f"Invalid lines: {invalid_lines}")
    print(f"Unique (departure_storage, departure_time) combinations: {len(flights_by_departure)}")
    print()
    
    # Find and report duplicates
    duplicates_found = False
    duplicate_count = 0
    
    for key, flights in sorted(flights_by_departure.items()):
        if len(flights) > 1:
            duplicates_found = True
            duplicate_count += len(flights) - 1
            
            departure_storage, departure_time = key
            print(f"🔴 DUPLICATE FOUND: {departure_storage} at {departure_time}")
            print(f"   Found {len(flights)} flights with same departure storage and time:")
            
            for flight in flights:
                print(f"   - Line {flight['line_num']}: {flight['full_line']}")
                print(f"     → Destination: {flight['arrival_storage']}, "
                      f"Arrival: {flight['arrival_time']}, Capacity: {flight['capacity']}")
            print()
    
    if not duplicates_found:
        print("✅ No duplicates found! All flights have unique (departure_storage, departure_time) combinations.")
    else:
        print("=" * 80)
        print(f"Total duplicate flights: {duplicate_count}")
        print("=" * 80)
    
    return flights_by_departure, duplicates_found

def main():
    # File path
    file_path = Path(__file__).parent / "data" / "flights.txt"
    
    if not file_path.exists():
        print(f"❌ Error: File not found at {file_path}")
        return
    
    print(f"📂 Reading file: {file_path}")
    print()
    
    analyze_flights(file_path)

if __name__ == "__main__":
    main()
