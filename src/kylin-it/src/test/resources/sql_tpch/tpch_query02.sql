--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
with q2_min_ps_supplycost as (
select
	p_partkey as min_p_partkey,
	min(ps_supplycost) as min_ps_supplycost
from
	tpch.part,
	tpch.partsupp,
	tpch.supplier,
	tpch.nation,
	tpch.region
where
	p_partkey = ps_partkey
	and s_suppkey = ps_suppkey
	and s_nationkey = n_nationkey
	and n_regionkey = r_regionkey
	and r_name = 'EUROPE'
group by
	p_partkey
)
select
	s_acctbal,
	s_name,
	n_name,
	p_partkey,
	p_mfgr,
	s_address,
	s_phone,
	s_comment
from
	tpch.part,
	tpch.supplier,
	tpch.partsupp,
	tpch.nation,
	tpch.region,
	q2_min_ps_supplycost
where
	p_partkey = ps_partkey
	and s_suppkey = ps_suppkey
	and p_size = 37
	and p_type like '%COPPER'
	and s_nationkey = n_nationkey
	and n_regionkey = r_regionkey
	and r_name = 'EUROPE'
	and ps_supplycost = min_ps_supplycost
	and p_partkey = min_p_partkey
order by
	s_acctbal desc,
	n_name,
	s_name,
	p_partkey
limit 100;